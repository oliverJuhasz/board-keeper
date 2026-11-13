-- A Solo Play recorded for score only, as in Calico and Harmonies, is neither won nor lost.
-- Its Result has no value for "won", where until now every Result had one.
alter table play_result
    alter column won drop not null;
