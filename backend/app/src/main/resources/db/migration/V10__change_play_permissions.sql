-- Changing a Play means editing or deleting it. A Member may change the Plays they recorded
-- and the Plays they took part in. Changing any Play at all (CHANGE_ANY_PLAY) is a Permission
-- of its own, which only the Admin Role holds until an Admin gives it to another Role.
insert into role_permission (role_id, permission)
select role.id, granted.permission
from role
         cross join (values ('CHANGE_PLAYS_RECORDED'), ('CHANGE_PLAYS_TAKEN_PART_IN')) as granted (permission)
where role.built_in = 'MEMBER';
