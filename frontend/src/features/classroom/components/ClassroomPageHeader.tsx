import React, { ReactNode } from 'react';

interface ClassroomPageHeaderProps {
  eyebrow: string;
  title: string;
  description: string;
  primaryAction?: ReactNode;
}

export const ClassroomPageHeader: React.FC<ClassroomPageHeaderProps> = ({
  eyebrow,
  title,
  description,
  primaryAction,
}) => {
  return (
    <div className="classroom-intro-header">
      <div className="classroom-intro-left">
        <span className="classroom-eyebrow">{eyebrow}</span>
        <h1 className="classroom-title">{title}</h1>
        <p className="classroom-subtitle">{description}</p>
      </div>

      {primaryAction && <div className="classroom-intro-right">{primaryAction}</div>}
    </div>
  );
};
