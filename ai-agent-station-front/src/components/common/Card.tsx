import React from 'react';
import styled from 'styled-components';
import { theme } from '../../styles/theme';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  padding?: 'sm' | 'base' | 'lg' | 'xl';
  shadow?: 'sm' | 'base' | 'md' | 'lg' | 'card';
  hover?: boolean;
  onClick?: () => void;
}

const StyledCard = styled.div<{
  $padding: string;
  $shadow: string;
  $hover: boolean;
  $clickable: boolean;
}>`
  background: ${theme.colors.bg.primary};
  border-radius: ${theme.borderRadius.lg};
  border: 1px solid ${theme.colors.border.secondary};
  box-shadow: ${props => theme.shadows[props.$shadow as keyof typeof theme.shadows]};
  padding: ${props => theme.spacing[props.$padding as keyof typeof theme.spacing]};
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  cursor: ${props => props.$clickable ? 'pointer' : 'default'};
  
  ${props => props.$hover && `
    &:hover {
      transform: translateY(-2px);
      box-shadow: ${theme.shadows.lg};
      border-color: ${theme.colors.primary};
    }
  `}
`;

export const Card: React.FC<CardProps> = ({
  children,
  className,
  padding = 'base',
  shadow = 'card',
  hover = false,
  onClick,
}) => {
  return (
    <StyledCard
      className={className}
      $padding={padding}
      $shadow={shadow}
      $hover={hover}
      $clickable={!!onClick}
      onClick={onClick}
    >
      {children}
    </StyledCard>
  );
};