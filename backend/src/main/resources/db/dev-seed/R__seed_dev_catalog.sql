-- =====================================================================
-- R__seed_dev_catalog.sql
-- Stage 1 DEVELOPMENT/DEMO seed data: fictional vendors + furniture
-- catalog (products + placeholder media), so the frontend catalog panel
-- and Geometry Engine have realistic data to work with locally.
--
-- This is a Flyway REPEATABLE migration (checksum-based re-run) living in
-- a separate location (classpath:db/dev-seed) that is only added to
-- spring.flyway.locations under the "local" profile (see
-- application-local.properties). It never runs against a production
-- deployment and never touches the production domain schema — this file
-- contains DML only, no DDL.
--
-- Idempotent: every INSERT is guarded so re-running this migration (e.g.
-- after Flyway detects a checksum change) never creates duplicate rows.
-- All fictional data — no proprietary real-world catalog content.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Vendors (3 fictional furniture vendors)
-- ---------------------------------------------------------------------
INSERT INTO vendor (id, name, country, website, logo_url, status)
SELECT gen_random_uuid(), v.name, v.country, v.website, v.logo_url, v.status
FROM (VALUES
    ('Nordbo Living', 'Sweden', 'https://example.com/nordbo-living',
        'https://placehold.co/240x80?text=Nordbo+Living', 'ACTIVE'),
    ('Casa Milano', 'Italy', 'https://example.com/casa-milano',
        'https://placehold.co/240x80?text=Casa+Milano', 'ACTIVE'),
    ('Oakwell & Co.', 'United States', 'https://example.com/oakwell-co',
        'https://placehold.co/240x80?text=Oakwell+%26+Co', 'ACTIVE')
) AS v(name, country, website, logo_url, status)
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------
-- Products (25 fictional furniture items across 11 categories)
-- ---------------------------------------------------------------------
INSERT INTO product (
    id, vendor_id, external_id, name, sku, category, collection, style,
    material, color, width_mm, depth_mm, height_mm, weight_grams,
    price_amount, price_currency, status
)
SELECT
    gen_random_uuid(), v.id, p.external_id, p.name, p.sku, p.category,
    p.collection, p.style, p.material, p.color, p.width_mm, p.depth_mm,
    p.height_mm, p.weight_grams, p.price_amount, p.price_currency, 'ACTIVE'
FROM (VALUES
    -- Nordbo Living (Scandinavian, EUR) ---------------------------------
    ('Nordbo Living', 'NB-SOFA-001', 'Kallby 3-Seater Sofa', 'NB-SF-KALLBY3', 'Sofa',
        'Kallby'::varchar(160), 'Scandinavian'::varchar(120), 'Polyester Fabric'::varchar(120), 'Slate Grey'::varchar(120),
        2100::numeric(10,2), 880::numeric(10,2), 850::numeric(10,2), 42000::numeric(10,2), 899.00::numeric(12,2), 'EUR'),
    ('Nordbo Living', 'NB-SOFA-002', 'Ystra 2-Seater Loveseat', 'NB-SF-YSTRA2', 'Sofa',
        'Ystra', 'Scandinavian', 'Boucle Fabric', 'Oatmeal',
        1650, 870, 820, 34000, 749.00, 'EUR'),
    ('Nordbo Living', 'NB-ARM-001', 'Fjora Lounge Armchair', 'NB-AC-FJORA', 'Armchair',
        'Fjora', 'Scandinavian', 'Wool Blend Fabric', 'Forest Green',
        780, 820, 870, 19500, 429.00, 'EUR'),
    ('Nordbo Living', 'NB-CT-001', 'Lindby Round Coffee Table', 'NB-CT-LINDBY', 'Coffee Table',
        'Lindby', 'Scandinavian', 'Oak Veneer', 'Natural Oak',
        900, 900, 420, 16500, 219.00, 'EUR'),
    ('Nordbo Living', 'NB-DT-001', 'Solberg Extendable Dining Table', 'NB-DT-SOLBERG', 'Dining Table',
        'Solberg', 'Scandinavian', 'Solid Ash Wood', 'Whitewashed Oak',
        1800, 900, 750, 34000, 649.00, 'EUR'),
    ('Nordbo Living', 'NB-CH-001', 'Alva Dining Chair', 'NB-CH-ALVA', 'Chair',
        'Alva', 'Scandinavian', 'Beechwood & Fabric', 'Charcoal',
        460, 520, 860, 5600, 89.00, 'EUR'),
    ('Nordbo Living', 'NB-BED-001', 'Moln Queen Bed Frame', 'NB-BD-MOLN', 'Bed',
        'Moln', 'Scandinavian', 'Solid Pine & Linen', 'Light Grey',
        1700, 2140, 1080, 48000, 549.00, 'EUR'),
    ('Nordbo Living', 'NB-WD-001', 'Ostra 3-Door Wardrobe', 'NB-WD-OSTRA3', 'Wardrobe',
        'Ostra', 'Scandinavian', 'Melamine-Faced Board', 'White',
        1500, 600, 2100, 78000, 429.00, 'EUR'),
    ('Nordbo Living', 'NB-LP-001', 'Solvind Floor Lamp', 'NB-LP-SOLVIND', 'Lamp',
        NULL::varchar(160), 'Scandinavian', 'Powder-Coated Steel', 'Matte Black',
        350, 350, 1550, 3800, 99.00, 'EUR'),

    -- Casa Milano (Contemporary Italian, EUR) ---------------------------
    ('Casa Milano', 'CM-SOFA-001', 'Trento Modular Sofa', 'CM-SF-TRENTO', 'Sofa',
        'Trento', 'Contemporary Italian', 'Top-Grain Leather', 'Cognac Brown',
        2200, 900, 780, 46000, 1899.00, 'EUR'),
    ('Casa Milano', 'CM-ARM-001', 'Bellagio Swivel Armchair', 'CM-AC-BELLAGIO', 'Armchair',
        'Bellagio', 'Contemporary Italian', 'Velvet', 'Emerald',
        820, 850, 900, 21000, 649.00, 'EUR'),
    ('Casa Milano', 'CM-CT-001', 'Portofino Marble-Top Coffee Table', 'CM-CT-PORTOFINO', 'Coffee Table',
        'Portofino', 'Contemporary Italian', 'Marble & Brass', 'Carrara White',
        1200, 600, 400, 38000, 899.00, 'EUR'),
    ('Casa Milano', 'CM-DT-001', 'Verona Glass Dining Table', 'CM-DT-VERONA', 'Dining Table',
        'Verona', 'Contemporary Italian', 'Tempered Glass & Steel', 'Clear / Chrome',
        2000, 1000, 750, 39000, 1299.00, 'EUR'),
    ('Casa Milano', 'CM-CH-001', 'Milano Leather Dining Chair', 'CM-CH-MILANO', 'Chair',
        'Milano', 'Contemporary Italian', 'Leather & Steel', 'Black',
        480, 550, 880, 6800, 149.00, 'EUR'),
    ('Casa Milano', 'CM-CB-001', 'Rialto Sideboard', 'CM-CB-RIALTO', 'Cabinet',
        'Rialto', 'Contemporary Italian', 'Walnut Veneer & Gold Metal', 'Walnut',
        1800, 420, 820, 44000, 1099.00, 'EUR'),
    ('Casa Milano', 'CM-DK-001', 'Amalfi Writing Desk', 'CM-DK-AMALFI', 'Desk',
        'Amalfi', 'Contemporary Italian', 'Lacquered MDF & Steel', 'Glossy White',
        1400, 650, 750, 26000, 379.00, 'EUR'),
    ('Casa Milano', 'CM-LP-001', 'Capri Table Lamp', 'CM-LP-CAPRI', 'Lamp',
        NULL, 'Contemporary Italian', 'Blown Glass & Brass', 'Amber',
        260, 260, 520, 1900, 129.00, 'EUR'),
    ('Casa Milano', 'CM-SH-001', 'Siena Wall Shelving Unit', 'CM-SH-SIENA', 'Shelf',
        'Siena', 'Contemporary Italian', 'Walnut Veneer & Steel', 'Walnut / Black',
        900, 320, 1900, 33000, 549.00, 'EUR'),

    -- Oakwell & Co. (American rustic/traditional, USD) ------------------
    ('Oakwell & Co.', 'OW-CH-001', 'Timberline Ladderback Chair', 'OW-CH-TIMBERLINE', 'Chair',
        'Timberline', 'Rustic', 'Solid Oak', 'Weathered Brown',
        480, 540, 950, 7200, 179.00, 'USD'),
    ('Oakwell & Co.', 'OW-BED-001', 'Redwood King Bed Frame', 'OW-BD-REDWOOD', 'Bed',
        'Redwood', 'Rustic', 'Solid Acacia Wood', 'Rustic Brown',
        2000, 2150, 1150, 58000, 999.00, 'USD'),
    ('Oakwell & Co.', 'OW-WD-001', 'Ashford 2-Door Wardrobe', 'OW-WD-ASHFORD2', 'Wardrobe',
        'Ashford', 'Traditional', 'Solid Pine', 'Espresso',
        1200, 600, 2000, 71000, 749.00, 'USD'),
    ('Oakwell & Co.', 'OW-CB-001', 'Brambleton Accent Cabinet', 'OW-CB-BRAMBLETON', 'Cabinet',
        'Brambleton', 'Rustic', 'Reclaimed Wood', 'Barnwood',
        900, 400, 900, 32000, 429.00, 'USD'),
    ('Oakwell & Co.', 'OW-DK-001', 'Hartwell Executive Desk', 'OW-DK-HARTWELL', 'Desk',
        'Hartwell', 'Traditional', 'Solid Walnut', 'Dark Walnut',
        1500, 700, 760, 41000, 899.00, 'USD'),
    ('Oakwell & Co.', 'OW-SH-001', 'Millbrook Ladder Bookshelf', 'OW-SH-MILLBROOK', 'Shelf',
        'Millbrook', 'Rustic', 'Solid Pine', 'Natural Pine',
        800, 350, 1850, 29500, 329.00, 'USD'),
    ('Oakwell & Co.', 'OW-LP-001', 'Prairie Floor Lamp', 'OW-LP-PRAIRIE', 'Lamp',
        NULL, 'Rustic', 'Wrought Iron & Linen Shade', 'Bronze',
        400, 400, 1650, 4200, 119.00, 'USD')
) AS p(
    vendor_name, external_id, name, sku, category, collection, style,
    material, color, width_mm, depth_mm, height_mm, weight_grams,
    price_amount, price_currency
)
JOIN vendor v ON v.name = p.vendor_name
ON CONFLICT (vendor_id, external_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Product media (one safe placeholder image per product)
-- Uses Lorem Picsum (https://picsum.photos), a free placeholder image
-- service commonly used for demo/dev data — not proprietary catalog content.
-- ---------------------------------------------------------------------
INSERT INTO product_media (id, product_id, url, type, alt_text, order_index)
SELECT gen_random_uuid(), p.id,
       'https://picsum.photos/seed/' || p.external_id || '/800/600',
       'IMAGE', p.name, 0
FROM product p
JOIN vendor v ON v.id = p.vendor_id
WHERE v.name IN ('Nordbo Living', 'Casa Milano', 'Oakwell & Co.')
  AND NOT EXISTS (
      SELECT 1 FROM product_media pm
      WHERE pm.product_id = p.id AND pm.order_index = 0
  );


