import React from 'react';

import { getCustomRolePermissions } from 'custom/roles/customRolePermissions';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';

import { REQUISITION_TEMPLATE_URL } from 'consts/applicationUrls';
import Translate from 'utils/Translate';

const StockListHeader = ({ isUserAdmin, customRolePermissions }) => {
  const permissions = getCustomRolePermissions({ customRolePermissions });

  return (
    <div className="d-flex list-page-header">
      <span className="d-flex align-self-center title">
        <Translate id="react.stocklists.header.label" defaultMessage="Stock List" />
      </span>
      <div className="d-flex justify-content-end buttons align-items-center">
        {
          (isUserAdmin || permissions.canManageStocklists) && (
            <a className="primary-button" href={`${REQUISITION_TEMPLATE_URL.create()}?type=STOCK`}>
              <Translate id="react.stocklists.addStockList.label" defaultMessage="Add stocklist" />
            </a>
          )
        }
      </div>
    </div>
  );
};

const mapStateToProps = (state) => ({
  isUserAdmin: state.session.isUserAdmin,
  customRolePermissions: state.session.customRolePermissions,
});

export default connect(mapStateToProps)(StockListHeader);

StockListHeader.propTypes = {
  isUserAdmin: PropTypes.bool.isRequired,
  customRolePermissions: PropTypes.shape({
    activeCustomRolePolicy: PropTypes.string,
    canManageStocklists: PropTypes.bool,
  }),
};

StockListHeader.defaultProps = {
  customRolePermissions: undefined,
};
