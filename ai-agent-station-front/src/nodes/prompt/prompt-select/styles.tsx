import styled from 'styled-components';

export const PromptPort = styled.div`
  position: absolute;
  right: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #52c41a;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #52c41a;
  cursor: pointer;
  z-index: 10;

  &:hover {
    background: #73d13d;
    box-shadow: 0 0 0 2px #73d13d;
  }
`;