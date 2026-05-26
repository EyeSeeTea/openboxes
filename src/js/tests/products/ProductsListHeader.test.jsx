import React from 'react';

import { render, screen } from '@testing-library/react';

import { ProductsListHeader } from 'components/products/ProductsListHeader';

describe('ProductsListHeader', () => {
  it('hides add and import actions for reporting user', () => {
    render(
      <ProductsListHeader
        isUserAdmin={false}
        customRolePermissions={{
          activeCustomRolePolicy: 'ROLE_REPORTING_USER',
          canManageProducts: false,
        }}
      />,
    );

    expect(screen.queryByText('Import products')).toBeNull();
    expect(screen.queryByText('Add product')).toBeNull();
  });

  it('hides add and import actions for regional warehouse user', () => {
    render(
      <ProductsListHeader
        isUserAdmin={false}
        customRolePermissions={{
          activeCustomRolePolicy: 'ROLE_REGIONAL_WAREHOUSE',
          canManageProducts: false,
        }}
      />,
    );

    expect(screen.queryByText('Import products')).toBeNull();
    expect(screen.queryByText('Add product')).toBeNull();
  });

  it('shows add and import actions for product write users', () => {
    render(
      <ProductsListHeader
        isUserAdmin={false}
        customRolePermissions={{
          activeCustomRolePolicy: 'ROLE_RPC_SUPERUSER',
          canManageProducts: true,
        }}
      />,
    );

    expect(screen.queryByText('Import products')).not.toBeNull();
    expect(screen.queryByText('Add product')).not.toBeNull();
  });
});
