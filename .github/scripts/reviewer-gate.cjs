'use strict';

/**
 * Strict per-person reviewer gate. GitHub clears requested_reviewers when someone
 * approves, so inspect the PR timeline rather than only the current requests.
 * A requested reviewer cannot be silently removed to bypass this policy.
 * Team requests fail closed: their semantics are not equivalent to requiring
 * every individually requested person to approve.
 */
function evaluateReviewerGate({ pull, timeline = [], reviews = [] }) {
  if (!pull || !pull.head || !pull.head.sha) {
    return { ok: false, reason: 'Cannot determine the latest PR head commit.' };
  }
  if (pull.draft) {
    return { ok: false, reason: 'Draft PR: mark it ready for review.' };
  }

  const author = (pull.user?.login || '').toLowerCase();
  const requested = new Map();
  let teamRequested = false;

  for (const event of timeline) {
    if (event.event !== 'review_requested') continue;
    if (event.requested_team?.slug) teamRequested = true;
    const username = event.requested_reviewer?.login?.toLowerCase();
    if (!username) continue;
    const timestamp = Date.parse(event.created_at || '') || 0;
    requested.set(username, Math.max(timestamp, requested.get(username) || 0));
  }

  for (const user of pull.requested_reviewers || []) {
    const username = user.login?.toLowerCase();
    if (username && !requested.has(username)) requested.set(username, 0);
  }
  if (pull.requested_teams?.length || pull.requested_team_reviewers?.length || teamRequested) {
    return {
      ok: false,
      reason: 'Team review requests are unsupported by this all-reviewers gate; request individuals.',
    };
  }
  if (requested.size === 0) {
    return { ok: false, reason: 'No reviewer was explicitly requested on this PR.' };
  }

  const pending = [];
  for (const [username, requestedAt] of requested) {
    if (username === author) {
      pending.push(username);
      continue;
    }
    const decisive = reviews
      .filter((review) =>
        review.user?.login?.toLowerCase() === username &&
        ['APPROVED', 'CHANGES_REQUESTED', 'DISMISSED'].includes(review.state?.toUpperCase())
      )
      .sort((a, b) =>
        (Date.parse(a.submitted_at || '') || 0) - (Date.parse(b.submitted_at || '') || 0) ||
        Number(a.id || 0) - Number(b.id || 0)
      );
    const latest = decisive.at(-1);
    const approvedAt = Date.parse(latest?.submitted_at || '') || 0;
    if (!latest || latest.state.toUpperCase() !== 'APPROVED' ||
        !approvedAt || approvedAt <= requestedAt || latest.commit_id !== pull.head.sha) {
      pending.push(username);
    }
  }
  if (pending.length) {
    return { ok: false, reason: `Approval missing/stale/dismissed: ${pending.join(', ')}` };
  }
  return { ok: true, reason: `All ${requested.size} explicitly requested reviewer(s) approved the latest commit.` };
}

module.exports = { evaluateReviewerGate };
