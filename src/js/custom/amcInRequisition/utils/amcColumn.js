// Pure helpers for the AMC (Average Monthly Consumption) column shown beside the
// Demand column on the requisition create (AddItemsPage) and edit (EditPage)
// screens. Kept in a custom module so the column logic stays isolated from
// upstream and is unit-testable independently of the wizard components.

// The wizard field configs wrap their column map under a single container key:
// AddItemsPage uses `lineItems`, EditPage uses `editPageItems`. Detect whichever
// key holds the `fields` map so these helpers work for both screens. Each config
// has exactly one such container; this returns the first match (and `undefined`
// if none), which callers treat as a no-op.
const getFieldsContainerKey = (config) => Object.keys(config).find(
  (key) => config[key] && typeof config[key] === 'object' && 'fields' in config[key],
);

// AMC is displayed to one decimal place to match the Consumption Report's
// `###.#` format (so the column reconciles with the report, and small monthly
// rates don't collapse to 0). The backend returns the raw SUM / windowDays * 30
// value; rounding is display-only.
export const formatAmc = (value) => {
  if (value === null || value === undefined || value === '') {
    return value;
  }
  return Math.round(value * 10) / 10;
};

const toWidth = (field) => Number(field && field.flexWidth) || 0;

// The EditPage configs render a grouped header (Request Information / Availability
// / Edit) whose flexWidth must equal the sum of the column flexWidths beneath it.
// Inserting a column without bumping its group's flexWidth misaligns the header
// from the body. This finds which group the anchor column falls under (by walking
// the columns in order against the cumulative group boundaries) and returns a new
// headerGroupings with that group's flexWidth increased by `extraWidth`.
const bumpHeaderGrouping = (headerGroupings, fields, anchorKey, extraWidth) => {
  let cumulative = 0;
  const groupEnds = Object.entries(headerGroupings).map(([name, group]) => {
    cumulative += Number(group.flexWidth) || 0;
    return { name, end: cumulative };
  });

  let offset = 0;
  let anchorGroupName = null;
  Object.entries(fields).some(([key, value]) => {
    if (key === anchorKey) {
      const match = groupEnds.find((groupEnd) => offset < groupEnd.end);
      anchorGroupName = match ? match.name : null;
      return true;
    }
    offset += toWidth(value);
    return false;
  });

  if (!anchorGroupName) {
    return headerGroupings;
  }
  const group = headerGroupings[anchorGroupName];
  return {
    ...headerGroupings,
    [anchorGroupName]: { ...group, flexWidth: (Number(group.flexWidth) || 0) + extraWidth },
  };
};

// EditPage: insert the AMC column immediately after the Demand column. The
// AD_HOCK config shows both a requesting and a fulfilling demand column — AMC
// sits beside the requesting one; the stocklist variants show only the
// fulfilling demand column, so AMC sits beside that. Also widens the matching
// header grouping so the grouped header stays aligned with the body. Returns a
// new config; the input is not mutated.
export const withAmcColumn = (config, amcField) => {
  const containerKey = getFieldsContainerKey(config);
  if (!containerKey) {
    return config;
  }
  const container = config[containerKey];
  const { fields } = container;
  const anchorKey = 'quantityDemandRequesting' in fields
    ? 'quantityDemandRequesting'
    : 'quantityDemandFulfilling';
  const newFields = Object.entries(fields).reduce((acc, [key, value]) => {
    const next = { ...acc, [key]: value };
    if (key === anchorKey) {
      return { ...next, amc: amcField };
    }
    return next;
  }, {});

  const newContainer = { ...container, fields: newFields };
  if (container.headerGroupings) {
    newContainer.headerGroupings = bumpHeaderGrouping(
      container.headerGroupings, fields, anchorKey, toWidth(amcField),
    );
  }
  return { ...config, [containerKey]: newContainer };
};

// AddItemsPage: the AMC column is declared statically in each field config (so
// it can be positioned and styled per variant); this removes it when the
// feature flag is off. Returns a new config; the input is not mutated.
export const stripAmcColumn = (config) => {
  const containerKey = getFieldsContainerKey(config);
  if (!containerKey) {
    return config;
  }
  const container = config[containerKey];
  const fields = { ...container.fields };
  delete fields.amc;
  return { ...config, [containerKey]: { ...container, fields } };
};
