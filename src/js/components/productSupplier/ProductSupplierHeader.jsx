import React from 'react';

import { getCustomRolePermissions } from 'custom/roles/customRolePermissions';
import { useSelector } from 'react-redux';

import Button from 'components/form-elements/Button';
import ListTitle from 'components/listPagesUtils/ListTitle';
import { PRODUCT_SUPPLIER_URL } from 'consts/applicationUrls';
import RoleType from 'consts/roleType';
import useUserHasPermissions from 'hooks/useUserHasPermissions';
import HeaderButtonsWrapper from 'wrappers/HeaderButtonsWrapper';
import HeaderWrapper from 'wrappers/HeaderWrapper';

const ProductSupplierHeader = () => {
  const customRolePermissions = useSelector((state) => state.session.customRolePermissions);
  const hasProductManagerPermissions = useUserHasPermissions({
    minRequiredRole: RoleType.ROLE_ADMIN,
    supplementalRoles: [RoleType.ROLE_PRODUCT_MANAGER],
  });
  const permissions = getCustomRolePermissions({ customRolePermissions });
  const canManageProducts = hasProductManagerPermissions || permissions.canManageProducts;

  return (
    <HeaderWrapper>
      <ListTitle label={{
        id: 'react.productSupplier.header.label',
        defaultMessage: 'Product Sources List',
      }}
      />
      <HeaderButtonsWrapper>
        {canManageProducts && (
          <Button
            label="react.productSupplier.createProductSource.label"
            defaultLabel="Create Product Source"
            onClick={() => {
              window.location = PRODUCT_SUPPLIER_URL.create();
            }}
          />
        )}
      </HeaderButtonsWrapper>
    </HeaderWrapper>
  );
};

export default ProductSupplierHeader;
