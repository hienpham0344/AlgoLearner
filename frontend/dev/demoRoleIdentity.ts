export type DemoRoleIdentity = 'student' | 'teacher' | 'admin';

export function demoRoleIdentity(value: string, mode: 'demo' | 'backend-mock'): DemoRoleIdentity | null {
  const identity = value.trim().toLowerCase();
  const name = identity.endsWith('@example.test') ? identity.slice(0, -'@example.test'.length) : identity;
  if (name === 'teacher' || name === 'admin') return name;
  if (mode === 'backend-mock' && name === 'student') return name;
  return null;
}
