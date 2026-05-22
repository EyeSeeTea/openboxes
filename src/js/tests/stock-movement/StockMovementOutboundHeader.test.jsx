import React from 'react';

import { render, screen } from '@testing-library/react';
import { BrowserRouter as Router } from 'react-router-dom';

import StockMovementOutboundHeader from 'components/stock-movement/outbound/StockMovementOutboundHeader';

describe('StockMovementOutboundHeader', () => {
  it('hides outbound creation action for reporting users', () => {
    render(
      <Router>
        <StockMovementOutboundHeader
          isRequestsOpen={false}
          showMyStockMovements={() => {}}
          customRolePermissions={{
            activeCustomRolePolicy: 'ROLE_REPORTING_USER',
            canCreateOutboundMovement: false,
          }}
        />
      </Router>,
    );

    expect(screen.queryByText('Create Stock Movement')).toBeNull();
  });

  it('shows outbound creation action when permitted', () => {
    render(
      <Router>
        <StockMovementOutboundHeader
          isRequestsOpen={false}
          showMyStockMovements={() => {}}
          customRolePermissions={{
            activeCustomRolePolicy: 'ROLE_RPC_SUPERUSER',
            canCreateOutboundMovement: true,
          }}
        />
      </Router>,
    );

    expect(screen.queryByText('Create Stock Movement')).not.toBeNull();
  });
});
