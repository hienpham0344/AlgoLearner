import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AuthFeedback } from '../components/AuthFeedback';

const topics = [
  { name: 'Python basics', code: 'def greet(name):\n    return f"Hello, {name}!"\n\nprint(greet("world"))', caption: 'Start with variables and functions. Build up to writing a small program of your own.' },
  { name: 'Algorithms', code: 'def find_largest(numbers):\n    largest = numbers[0]\n    for number in numbers:\n        largest = max(largest, number)\n    return largest', caption: 'Break a problem into smaller steps. Explore how different solutions use time and memory.' },
  { name: 'Practice', code: 'def is_even(number):\n    return number % 2 == 0\n\nassert is_even(4)\nassert not is_even(7)', caption: 'Turn an idea into working code. Use a few test cases to understand what happens at the edges.' },
] as const;

export const CoursePreviewPage = () => {
  const [selected, setSelected] = useState(0);
  return (
    <>
      <h1 tabIndex={-1}>Find your starting point.</h1>
      <AuthFeedback description="A preview of the concepts you can practice." />
      <div className="auth-topic-list" role="tablist" aria-label="Learning topics">
        {topics.map((topic, index) => <button key={topic.name} id={`topic-${index}`} type="button" role="tab" tabIndex={selected === index ? 0 : -1} aria-selected={selected === index} aria-controls="topic-example" onClick={() => setSelected(index)} onKeyDown={event => { if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { event.preventDefault(); const next = (index + (event.key === 'ArrowRight' ? 1 : -1) + topics.length) % topics.length; setSelected(next); document.getElementById(`topic-${next}`)?.focus(); } }}>{topic.name}</button>)}
      </div>
      <div role="tabpanel" id="topic-example" aria-labelledby={`topic-${selected}`} tabIndex={0}>
        <pre className="auth-course-example"><code>{topics[selected].code}</code></pre>
        <p className="auth-topic-caption">{topics[selected].caption}</p>
      </div>
      <div className="auth-course-cta"><Link className="auth-submit" to="/register">Create an account to get started</Link></div>
      <div className="auth-secondary"><Link to="/login">Back to sign in</Link></div>
    </>
  );
};
