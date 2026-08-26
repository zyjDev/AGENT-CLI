import styled from 'styled-components';

export const AdvisorPort = styled.div`
  position: absolute;
  right: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1890ff;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #1890ff;
  cursor: pointer;
  z-index: 10;

  &:hover {
    background: #40a9ff;
    box-shadow: 0 0 0 2px #40a9ff;
  }
`;