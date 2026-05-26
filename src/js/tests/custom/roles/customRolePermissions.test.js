import {
  canCreateInboundFromPurchaseOrder,
  canCreateInboundMovement,
  canCreateOutboundMovement,
  canManageProducts,
  canManagePurchasing,
  canManageStocklists,
  canSendStocklistEmail,
  canUseSuperuserPurchasingActions,
  DEFAULT_CUSTOM_ROLE_PERMISSIONS,
  getCustomRolePermissions,
} from 'custom/roles/customRolePermissions';

describe('customRolePermissions helper', () => {
  it('returns defaults when session has no custom payload', () => {
    const permissions = getCustomRolePermissions({});

    expect(permissions).toEqual(DEFAULT_CUSTOM_ROLE_PERMISSIONS);
    expect(canManageProducts({})).toBe(false);
    expect(canManageStocklists({})).toBe(false);
    expect(canUseSuperuserPurchasingActions({})).toBe(false);
  });

  it('returns backend-provided custom permissions', () => {
    const session = {
      customRolePermissions: {
        activeCustomRolePolicy: 'ROLE_REPORTING_USER',
        canCreateInboundMovement: false,
        canCreateInboundFromPurchaseOrder: false,
        canCreateOutboundMovement: false,
        canManageProducts: false,
        canManagePurchasing: false,
        canManageStocklists: false,
        canSendStocklistEmail: false,
        canUseSuperuserPurchasingActions: false,
      },
    };

    expect(canCreateInboundMovement(session)).toBe(false);
    expect(canCreateInboundFromPurchaseOrder(session)).toBe(false);
    expect(canCreateOutboundMovement(session)).toBe(false);
    expect(canManageProducts(session)).toBe(false);
    expect(canManagePurchasing(session)).toBe(false);
    expect(canManageStocklists(session)).toBe(false);
    expect(canSendStocklistEmail(session)).toBe(false);
    expect(canUseSuperuserPurchasingActions(session)).toBe(false);
  });

  it('returns backend-provided elevated purchasing visibility', () => {
    const session = {
      customRolePermissions: {
        canUseSuperuserPurchasingActions: true,
      },
    };

    expect(canUseSuperuserPurchasingActions(session)).toBe(true);
  });
});
