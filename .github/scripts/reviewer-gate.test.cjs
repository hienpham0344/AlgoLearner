'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { evaluateReviewerGate } = require('./reviewer-gate.cjs');
const HEAD = '0123456789abcdef';
const pull = (extra = {}) => ({ draft: false, user: { login: 'author' }, head: { sha: HEAD }, requested_reviewers: [], ...extra });
const requested = (login, created_at = '2026-11-08T01:00:00Z') => ({event: 'review_requested', requested_reviewer: { login }, created_at});
const review = (login, state = 'APPROVED', extra = {}) => ({ id: 1, user: { login }, state, submitted_at: '2026-11-08T02:00:00Z', commit_id: HEAD, ...extra });

// Five explicitly requested acceptance cases.
test('Draft PR is blocked even with an approval', () => {
  assert.equal(evaluateReviewerGate({pull: pull({draft: true}), timeline: [requested('alice')], reviews: [review('alice')]}).ok, false);
});
test('PR with no requested reviewer is blocked even with unsolicited approval', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [], reviews: [review('alice')]}).ok, false);
});
test('Any requested reviewer still pending blocks, even when one reviewer approved', () => {
  const result = evaluateReviewerGate({pull: pull({requested_reviewers:[{login:'bob'}]}), timeline: [requested('alice'), requested('bob')], reviews: [review('alice')]});
  assert.equal(result.ok, false);
  assert.match(result.reason, /bob/);
});
test('All requested reviewers approved the current HEAD passes (requests automatically cleared)', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice'), requested('bob')], reviews: [review('alice'), review('bob', 'APPROVED', { id: 2 })]}).ok, true);
});
test('Dismissed approval blocks again', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice')], reviews: [review('alice', 'DISMISSED')]}).ok, false);
});

test('Old approval is invalidated after a newer commit', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice')], reviews: [review('alice', 'APPROVED', {commit_id:'oldsha'})]}).ok, false);
});
test('Re-requesting review after approval requires new approval', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice', '2026-11-08T03:00:00Z')], reviews: [review('alice')]}).ok, false);
});
test('Removing review request cannot bypass an unapproved historical request', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice'), requested('bob')], reviews: [review('alice')]}).ok, false);
});
test('A later requested-changes review blocks an earlier approval', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('alice')], reviews: [review('alice'), review('alice','CHANGES_REQUESTED', {id:2, submitted_at:'2026-11-08T03:00:00Z'})]}).ok, false);
});
test('Team requests fail closed rather than pretending all team members approved', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [{event:'review_requested',requested_team:{slug:'devs'}}], reviews: [review('alice')]}).ok, false);
});
test('Reviewer cannot be PR author', () => {
  assert.equal(evaluateReviewerGate({pull: pull(), timeline: [requested('author')], reviews: [review('author')]}).ok, false);
});
