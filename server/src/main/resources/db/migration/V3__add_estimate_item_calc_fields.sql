ALTER TABLE estimate_items ADD COLUMN calculation_method VARCHAR(255);
ALTER TABLE estimate_items ADD COLUMN waste_percent NUMERIC(19, 4);
ALTER TABLE estimate_items ADD COLUMN layers INT;
ALTER TABLE estimate_items ADD COLUMN thickness_meters NUMERIC(19, 4);
ALTER TABLE estimate_items ADD COLUMN manual_quantity NUMERIC(19, 6);
ALTER TABLE estimate_items ADD COLUMN coverage_per_piece NUMERIC(19, 6);
ALTER TABLE estimate_items ADD COLUMN coverage_per_package NUMERIC(19, 6);
ALTER TABLE estimate_items ADD COLUMN package_size NUMERIC(19, 6);
ALTER TABLE estimate_items ADD COLUMN formula_description VARCHAR(1000);
