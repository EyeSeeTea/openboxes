export const DEFAULT_CUSTOM_ROLE_PERMISSIONS = {
  activeCustomRolePolicy: null,
  canCreateInboundMovement: true,
  canCreateInboundFromPurchaseOrder: true,
  canCreateOutboundMovement: true,
  canManageProducts: false,
  canManagePurchasing: true,
  canManageStocklists: false,
  canSendStocklistEmail: true,
};

export const getCustomRolePermissions = (session = {}) => ({
  ...DEFAULT_CUSTOM_ROLE_PERMISSIONS,
  ...(session.customRolePermissions || {}),
});

export const canCreateInboundMovement = (session = {}) =>
  getCustomRolePermissions(session).canCreateInboundMovement;

export const canCreateInboundFromPurchaseOrder = (session = {}) =>
  getCustomRolePermissions(session).canCreateInboundFromPurchaseOrder;

export const canManageStocklists = (session = {}) =>
  getCustomRolePermissions(session).canManageStocklists;

export const canSendStocklistEmail = (session = {}) =>
  getCustomRolePermissions(session).canSendStocklistEmail;

export const canCreateOutboundMovement = (session = {}) =>
  getCustomRolePermissions(session).canCreateOutboundMovement;

export const canManageProducts = (session = {}) =>
  getCustomRolePermissions(session).canManageProducts;

export const canManagePurchasing = (session = {}) =>
  getCustomRolePermissions(session).canManagePurchasing;
