-- ============================================================================
-- MedKernel 首次发布 / 灾备：清空 MEDKERNEL schema（只用本 schema 自身权限，无需 DBA）
--
-- 用法（任意能连库、装了 Oracle 客户端 sqlplus 的机器）：
--   sqlplus medkernel/<密码>@//<host>:1521/<service> @purge-schema.sql
--   （Windows 上若 sqlplus 报 "Error 46 / HTTP proxy"，先清空 HTTP_PROXY/HTTPS_PROXY 环境变量）
--
-- 前置：先停应用  systemctl stop medkernel  （避免连接占用 / 半迁移）。
-- 之后启动应用，Flyway 会从 V1 重建全部表与种子（内置超管 5 维 RBAC、平台主租户 t-1、
-- 菜单权限目录、系统配置）= 全新首次发布的初始化数据。
--
-- 警告：会删除当前用户下所有对象与回收站内容，不可逆！务必确认连接的就是 MEDKERNEL 这个
--       业务 schema，切勿连到共享实例里别的用户。
-- ============================================================================
set serveroutput on size unlimited
set echo off feedback off heading off linesize 200 pagesize 0 trimspool on
whenever sqlerror continue

prompt === BEFORE ===
select 'USER='||user from dual;
select 'OBJ_BEFORE='||count(*) from user_objects;
select 'RB_BEFORE='||count(*) from recyclebin;

prompt === purge recyclebin (pass 1) ===
purge recyclebin;

prompt === drop remaining objects ===
begin
  for r in (select object_name from user_objects where object_type='TABLE') loop
    begin execute immediate 'DROP TABLE "'||r.object_name||'" CASCADE CONSTRAINTS PURGE';
    exception when others then dbms_output.put_line('skip TABLE '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='VIEW') loop
    begin execute immediate 'DROP VIEW "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip VIEW '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='MATERIALIZED VIEW') loop
    begin execute immediate 'DROP MATERIALIZED VIEW "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip MVIEW '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='SEQUENCE') loop
    begin execute immediate 'DROP SEQUENCE "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip SEQ '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='SYNONYM') loop
    begin execute immediate 'DROP SYNONYM "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip SYN '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='PROCEDURE') loop
    begin execute immediate 'DROP PROCEDURE "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip PROC '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='FUNCTION') loop
    begin execute immediate 'DROP FUNCTION "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip FUNC '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type='PACKAGE') loop
    begin execute immediate 'DROP PACKAGE "'||r.object_name||'"';
    exception when others then dbms_output.put_line('skip PKG '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select object_name from user_objects where object_type like 'TYPE%') loop
    begin execute immediate 'DROP TYPE "'||r.object_name||'" FORCE';
    exception when others then dbms_output.put_line('skip TYPE '||r.object_name||': '||sqlerrm); end;
  end loop;
  for r in (select trigger_name from user_triggers) loop
    begin execute immediate 'DROP TRIGGER "'||r.trigger_name||'"';
    exception when others then dbms_output.put_line('skip TRIG '||r.trigger_name||': '||sqlerrm); end;
  end loop;
  for r in (select db_link from user_db_links) loop
    begin execute immediate 'DROP DATABASE LINK "'||r.db_link||'"';
    exception when others then dbms_output.put_line('skip DBLINK '||r.db_link||': '||sqlerrm); end;
  end loop;
end;
/

prompt === purge recyclebin (pass 2) ===
purge recyclebin;

prompt === AFTER（OBJ_AFTER / RB_AFTER 应为 0）===
select 'OBJ_AFTER='||count(*) from user_objects;
select 'RB_AFTER='||count(*) from recyclebin;
prompt === remaining by type（应为空）===
select object_type||' : '||count(*) from user_objects group by object_type order by 1;
exit
