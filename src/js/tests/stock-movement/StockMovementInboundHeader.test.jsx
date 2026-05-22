import React from 'react';

import { render, screen } from '@testing-library/react';
import { BrowserRouter as Router } from 'react-router-dom';

import StockMovementInboundHeader from 'components/stock-movement/inbound/StockMovementInboundHeader';

describe('StockMovementInboundHeader', () => {
  it('hides inbound creation actions for restricted custom roles', () => {
    const { rerender } = render(
      <Router>
        <StockMovementInboundHeader
          showMyStockMovements={() => {}}
          customRolePermissions={{
            activeCustomRolePolicy: 'ROLE_REGIONAL_WAREHOUSE',
            canCreateInboundMovement: false,
            canCreateInboundFromPurchaseOrder: false,
          }}
        />
      </Router>,
    );

    expect(screen.queryByText('Create Shipment from PO')).toBeNull();
    expect(screen.queryByText('Create Stock Movement')).toBeNull();

    rerender(
      <Router>
        <StockMovementInboundHeader
          showMyStockMovements={() => {}}
          customRolePermissions={{
            activeCustomRolePolicy: 'ROLE_FACILITY_STOREKEEPER',
            canCreateInboundMovement: false,
            canCreateInboundFromPurchaseOrder: false,
          }}
        />
      </Router>,
    );

    expect(screen.queryByText('Create Shipment from PO')).toBeNull();
    expect(screen.queryByText('Create Stock Movement')).toBeNull();
  });

  it('shows inbound creation actions when permitted', () => {
    render(
      <Router>
        <StockMovementInboundHeader
          showMyStockMovements={() => {}}
          customRolePermissions={{
            activeCustomRolePolicy: 'ROLE_RPC_SUPERUSER',
            canCreateInboundMovement: true,
            canCreateInboundFromPurchaseOrder: true,
          }}
        />
      </Router>,
    );

    expect(screen.queryByText('Create Shipment from PO')).not.toBeNull();
    expect(screen.queryByText('Create Stock Movement')).not.toBeNull();
  });
});
