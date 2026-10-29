CREATE TABLE reconciliation_runs (
    id                              BIGSERIAL PRIMARY KEY,
    started_at                      TIMESTAMPTZ   NOT NULL,
    duration_millis                 BIGINT        NOT NULL,
    healthy                         BOOLEAN       NOT NULL,
    assets_not_netting              INT           NOT NULL,
    accounts_not_matching_postings  INT           NOT NULL,
    accounts_with_wrong_holds       INT           NOT NULL,
    detail                          VARCHAR(2000)
);
