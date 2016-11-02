package org.blazer.userservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.blazer.userservice.body.PageBody;
import org.blazer.userservice.body.PermissionsTreeBody;
import org.blazer.userservice.cache.SystemCache;
import org.blazer.userservice.cache.UserCache;
import org.blazer.userservice.entity.USPermissions;
import org.blazer.userservice.entity.USRole;
import org.blazer.userservice.entity.USSystem;
import org.blazer.userservice.entity.USUser;
import org.blazer.userservice.exception.DuplicateKey;
import org.blazer.userservice.exception.NotAllowDeleteException;
import org.blazer.userservice.core.util.DesUtil;
import org.blazer.userservice.util.HMap;
import org.blazer.userservice.util.IntegerUtil;
import org.blazer.userservice.util.SqlUtil;
import org.blazer.userservice.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service(value = "userService")
public class UserService implements InitializingBean {

	private static Logger logger = LoggerFactory.getLogger(UserService.class);

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserCache userCache;

	@Autowired
	SystemCache systemCache;

	@Value("#{systemProperties.new_user_defalut_password}")
	private String _newUserDefaultPassword;

	private String newUserDefaultPassword;

	@Override
	public void afterPropertiesSet() throws Exception {
		newUserDefaultPassword = DesUtil.encrypt(_newUserDefaultPassword);
	}

	/**
	 * TODO : 系统相关
	 */

	public List<USSystem> findSystemAll() {
		try {
			String sql = "select * from us_system where enable=1";
			List<Map<String, Object>> rst = jdbcTemplate.queryForList(sql);
			List<USSystem> list = HMap.toList(rst, USSystem.class);
			logger.debug("rst size : " + rst.size());
			if (list.size() > 0) {
				logger.debug("system : " + list.get(0));
			}
			return list;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return new ArrayList<USSystem>();
	}

	public PageBody<USSystem> findSystemByPage(HashMap<String, String> params) {
		PageBody<USSystem> pb = new PageBody<USSystem>();
		String where = " where 1=1 and enable=1 ";
		String systemName = StringUtil.getStr(params.get("systemName"));
		if (systemName != null) {
			where += String.format(" and (system_name like '%%%s%%')", systemName);
		}
		String sql = "select * from us_system " + where + " limit ?,?";
		int start = (IntegerUtil.getInt1(params.get("page")) - 1) * IntegerUtil.getInt0(params.get("rows"));
		int end = IntegerUtil.getInt0(params.get("rows"));
		logger.debug("start : " + start);
		logger.debug("end : " + end);
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, start, end);
		logger.debug("list size : " + list.size());
		List<USSystem> systemList = new ArrayList<USSystem>();
		for (Map<String, Object> map : list) {
			USSystem system = new USSystem();
			system.setId(IntegerUtil.getInt0(map.get("id")));
			system.setIndexUrl(StringUtil.getStrEmpty(map.get("index_url")));
			system.setSystemName(StringUtil.getStrEmpty(map.get("system_name")));
			system.setRemark(StringUtil.getStrEmpty(map.get("remark")));
			systemList.add(system);
		}
		pb.setTotal(IntegerUtil.getInt0(jdbcTemplate.queryForList("select count(0) as ct from us_system " + where).get(0).get("ct")));
		pb.setRows(systemList);
		logger.debug(pb.toString());
		return pb;
	}

	public void saveSystem(USSystem system) {
		logger.debug("system " + system);
		if (system.getId() == null) {
			// enable 数据库默认值1
			String sql = "insert into us_system(system_name,index_url,remark) values(?,?,?)";
			jdbcTemplate.update(sql, system.getSystemName(), system.getIndexUrl(), system.getRemark());
		} else {
			String sql = "update us_system set system_name=?,index_url=?,remark=? where id=?";
			jdbcTemplate.update(sql, system.getSystemName(), system.getIndexUrl(), system.getRemark(), system.getId());
		}
	}

	public void delSystem(Integer id) throws Exception {
		logger.debug("systemId " + id);
		String sql = "select count(0) as ct from us_permissions where enable=1 and system_id=?";
		logger.debug(SqlUtil.Show(sql, id));
		Integer count = IntegerUtil.getInt0(jdbcTemplate.queryForList(sql, id).get(0).get("ct"));
		logger.debug("permissions count : " + count);
		if (count != 0) {
			throw new NotAllowDeleteException("该系统下有[" + count + "]个权限，不能删除。");
		}
		sql = "update us_system set enable=0 where id=?";
		logger.debug(SqlUtil.Show(sql, id));
		jdbcTemplate.update(sql, id);
	}

	public USSystem findSystemById(Integer id) {
		String sql = "select * from us_system where id=? and enable=1";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
		USSystem system = new USSystem();
		if (list.size() == 0) {
			return system;
		}
		Map<String, Object> map = list.get(0);
		system.setId(IntegerUtil.getInt0(map.get("id")));
		system.setSystemName(StringUtil.getStrEmpty(map.get("system_name")));
		system.setIndexUrl(StringUtil.getStrEmpty(map.get("index_url")));
		system.setRemark(StringUtil.getStrEmpty(map.get("remark")));
		return system;
	}

	public USSystem findSystemById(HashMap<String, String> params) {
		return findSystemById(IntegerUtil.getInt0(params.get("id")));
	}

	/**
	 * TODO : 用户相关
	 */

	public PageBody<USUser> findUserByPage(HashMap<String, String> params) {
		PageBody<USUser> pb = new PageBody<USUser>();
		String where = " where 1=1 and enable=1 ";
		String userName = StringUtil.getStr(params.get("userName"));
		if (userName != null) {
			where += String.format(" and (user_name like '%%%s%%' or user_name_cn like '%%%s%%' or phone_number like '%%%s%%' or email like '%%%s%%')",
					userName, userName, userName, userName);
		}
		String sql = "select * from us_user " + where + " limit ?,?";
		int start = (IntegerUtil.getInt1(params.get("page")) - 1) * IntegerUtil.getInt0(params.get("rows"));
		int end = IntegerUtil.getInt0(params.get("rows"));
		logger.debug("start : " + start);
		logger.debug("end : " + end);
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, start, end);
		logger.debug("list size : " + list.size());
		List<USUser> userList = new ArrayList<USUser>();
		for (Map<String, Object> map : list) {
			USUser user = new USUser();
			user.setId(IntegerUtil.getInt0(map.get("id")));
			user.setUserName(StringUtil.getStrEmpty(map.get("user_name")));
			user.setUserNameCn(StringUtil.getStrEmpty(map.get("user_name_cn")));
			// user.setPassword(StringUtil.getStrEmpty(map.get("password")));
			user.setPhoneNumber(StringUtil.getStrEmpty(map.get("phone_number")));
			user.setEmail(StringUtil.getStrEmpty(map.get("email")));
			user.setRemark(StringUtil.getStrEmpty(map.get("remark")));
			userList.add(user);
		}
		pb.setTotal(IntegerUtil.getInt0(jdbcTemplate.queryForList("select count(0) as ct from us_user " + where).get(0).get("ct")));
		pb.setRows(userList);
		logger.debug(pb.toString());
		return pb;
	}

	public USUser findUserById(HashMap<String, String> params) {
		String sql = "select * from us_user where id = ? and enable=1";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, IntegerUtil.getInt0(params.get("id")));
		USUser user = new USUser();
		if (list.size() == 0) {
			return user;
		}
		Map<String, Object> map = list.get(0);
		user.setId(IntegerUtil.getInt0(map.get("id")));
		user.setUserName(StringUtil.getStrEmpty(map.get("user_name")));
		user.setUserNameCn(StringUtil.getStrEmpty(map.get("user_name_cn")));
		// user.setPassword(StringUtil.getStrEmpty(map.get("password")));
		user.setPhoneNumber(StringUtil.getStrEmpty(map.get("phone_number")));
		user.setEmail(StringUtil.getStrEmpty(map.get("email")));
		user.setRemark(StringUtil.getStrEmpty(map.get("remark")));
		return user;
	}

	public void saveUser(USUser user, String roleIds) throws DuplicateKey {
		// 验证是否重名
		Integer userId = null;
		if (user.getId() == null) {
			String checkSql = "select 1 from us_user where enable=1 and user_name=? ";
			List<Map<String, Object>> rst = jdbcTemplate.queryForList(checkSql, user.getUserName());
			if (rst != null && rst.size() != 0) {
				throw new DuplicateKey("已经存在该用户名！");
			}
			// enable 数据库默认值1
			String sql = "insert into us_user(user_name,user_name_cn,password,email,phone_number,remark) values(?,?,?,?,?,?)";
			jdbcTemplate.update(sql, user.getUserName(), user.getUserNameCn(), newUserDefaultPassword, user.getEmail(), user.getPhoneNumber(), user.getRemark());
			userId = IntegerUtil.getInt0(jdbcTemplate.queryForMap("select max(id) as id from us_user where enable=1").get("id"));
		} else {
			String checkSql = "select 1 from us_user where enable=1 and user_name=? and id != ?";
			List<Map<String, Object>> rst = jdbcTemplate.queryForList(checkSql, user.getUserName(), user.getId());
			if (rst != null && rst.size() != 0) {
				throw new DuplicateKey("已经存在该用户名！");
			}
			userId = user.getId();
			String sql = "update us_user set user_name=?,user_name_cn=?,email=?,phone_number=?,remark=? where id=? and enable=1";
			jdbcTemplate.update(sql, user.getUserName(), user.getUserNameCn(), user.getEmail(), user.getPhoneNumber(), user.getRemark(), user.getId());
		}
		addUserRole(userId, roleIds);
		userCache.init(userId);
	}

	public void updatePwd(USUser user) {
		String sql = "update us_user set password=? where id=? and enable=1";
		jdbcTemplate.update(sql, user.getPassword(), user.getId());
		userCache.init(user.getId());
	}

	public void initPwd(Integer userId) {
		String sql = "update us_user set password=? where id=? and enable=1";
		jdbcTemplate.update(sql, newUserDefaultPassword, userId);
		userCache.init(userId);
	}

	public void delUser(Integer id) {
		logger.debug("userId " + id);
		String sql = "update us_user set enable=0 where id=?";
		jdbcTemplate.update(sql, id);
		// 删除用户角色关系
		delUserRoleByUserId(id);
	}

	public void delUserRoleByUserId(Integer id) {
		logger.debug("userId " + id);
		String sql = "delete from us_user_role where user_id=?";
		jdbcTemplate.update(sql, id);
	}

	public void addUserRole(Integer userId, String roleIds) {
		logger.debug("userId " + userId);
		logger.debug("roleIds " + roleIds);
		delUserRoleByUserId(userId);
		String sql = "insert into us_user_role(user_id, role_id) values(?, ?)";
		if (StringUtils.isNotEmpty(roleIds)) {
			for (String id : StringUtils.split(roleIds, ",")) {
				if (IntegerUtil.getInt(id) != null) {
					logger.debug(SqlUtil.Show(sql, userId, IntegerUtil.getInt(id)));
					jdbcTemplate.update(sql, userId, IntegerUtil.getInt(id));
				}
			}
		}
	}

	public String findRoleByUserId(Integer userId) {
		logger.debug("userId " + userId);
		String sql = "select GROUP_CONCAT(role_id) as roleids from us_user_role where user_id=? group by user_id";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, userId);
		String roleIds = list.size() == 0 ? "" : StringUtil.getStrEmpty(list.get(0).get("roleids"));
		logger.debug(roleIds);
		return roleIds;
	}

	/**
	 * TODO : 角色相关
	 */

	public List<USRole> findRoleAll() {
		try {
			String sql = "select * from us_role where enable=1";
			List<Map<String, Object>> rst = jdbcTemplate.queryForList(sql);
			List<USRole> list = HMap.toList(rst, USRole.class);
			logger.debug("rst size : " + rst.size());
			if (list.size() > 0) {
				logger.debug("role : " + list.get(0));
			}
			return list;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return new ArrayList<USRole>();
	}

	public PageBody<USRole> findRoleByPage(HashMap<String, String> params) {
		PageBody<USRole> pb = new PageBody<USRole>();
		String where = " where 1=1 and enable = 1 ";
		String roleName = StringUtil.getStr(params.get("roleName"));
		if (roleName != null) {
			where += String.format(" and (role_name like '%%%s%%')", roleName);
		}
		String sql = "select * from us_role " + where + " limit ?,?";
		int start = (IntegerUtil.getInt1(params.get("page")) - 1) * IntegerUtil.getInt0(params.get("rows"));
		int end = IntegerUtil.getInt0(params.get("rows"));
		logger.debug("start : " + start);
		logger.debug("end : " + end);
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, start, end);
		logger.debug("list size : " + list.size());
		List<USRole> roleList = new ArrayList<USRole>();
		for (Map<String, Object> map : list) {
			USRole role = new USRole();
			role.setId(IntegerUtil.getInt0(map.get("id")));
			role.setRoleName(StringUtil.getStrEmpty(map.get("role_name")));
			role.setRemark(StringUtil.getStrEmpty(map.get("remark")));
			roleList.add(role);
		}
		pb.setTotal(IntegerUtil.getInt0(jdbcTemplate.queryForList("select count(0) as ct from us_role " + where).get(0).get("ct")));
		pb.setRows(roleList);
		logger.debug(pb.toString());
		return pb;
	}

	public void saveRole(USRole role) {
		if (role.getId() == null) {
			// enable 数据库默认值1
			String sql = "insert into us_role(role_name,remark) values(?,?)";
			jdbcTemplate.update(sql, role.getRoleName(), role.getRemark());
		} else {
			String sql = "update us_role set role_name=?,remark=? where id=?";
			jdbcTemplate.update(sql, role.getRoleName(), role.getRemark(), role.getId());
		}
	}

	public void delRole(Integer id) throws Exception {
		logger.debug("roleId " + id);
		String sql = "select count(0) as ct from us_user_role ur inner join (select id from us_user where enable=1) u on ur.user_id=u.id where role_id=?";
		logger.debug(SqlUtil.Show(sql, id));
		Integer count = IntegerUtil.getInt0(jdbcTemplate.queryForList(sql, id).get(0).get("ct"));
		logger.debug("user count : " + count);
		if (count != 0) {
			throw new NotAllowDeleteException("该角色下有[" + count + "]个用户，不能删除。");
		}
		sql = "update us_role set enable=0 where id=?";
		logger.debug(SqlUtil.Show(sql, id));
		jdbcTemplate.update(sql, id);
		// 删除角色权限关系
		delRolePermissionsByRoleId(id);
	}

	public void delRolePermissionsByRoleId(Integer id) {
		String sql = "delete from us_role_permissions where role_id=?";
		logger.debug(SqlUtil.Show(sql, id));
		jdbcTemplate.update(sql, id);
	}

	public USRole findRoleById(HashMap<String, String> params) {
		String sql = "select * from us_role where id=? and enable=1";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, IntegerUtil.getInt0(params.get("id")));
		USRole role = new USRole();
		if (list.size() == 0) {
			return role;
		}
		Map<String, Object> map = list.get(0);
		role.setId(IntegerUtil.getInt0(map.get("id")));
		role.setRoleName(StringUtil.getStrEmpty(map.get("role_name")));
		role.setRemark(StringUtil.getStrEmpty(map.get("remark")));
		return role;
	}

	/**
	 * TODO : 权限相关
	 * @throws DuplicateKey 
	 */

	public void savePermissions(USPermissions permissions) throws DuplicateKey {
		if (permissions.getId() == null) {
			String checkSql = "select 1 from us_permissions where enable=1 and system_id=? and url=?";
			List<Map<String, Object>> rst = jdbcTemplate.queryForList(checkSql, permissions.getSystemId(), permissions.getUrl());
			if (rst != null && rst.size() != 0) {
				throw new DuplicateKey("已经存在该URL！");
			}
			// enable 数据库默认值1
			String sql = "insert into us_permissions(system_id,parent_id,permissions_name,url,remark) values(?,?,?,?,?)";
			jdbcTemplate.update(sql, permissions.getSystemId(), permissions.getParentId(), permissions.getPermissionsName(), permissions.getUrl(), permissions.getRemark());
			logger.debug(SqlUtil.Show(sql, permissions.getSystemId(), permissions.getParentId(), permissions.getPermissionsName(), permissions.getUrl(), permissions.getRemark()));
		} else {
			String sql = "update us_permissions set permissions_name=?,url=?,remark=? where id=?";
			jdbcTemplate.update(sql, permissions.getPermissionsName(), permissions.getUrl(), permissions.getRemark(), permissions.getId());
			logger.debug(SqlUtil.Show(sql, permissions.getPermissionsName(), permissions.getUrl(), permissions.getRemark(), permissions.getId()));
		}
	}

	public void delPermissions(Integer id) throws NotAllowDeleteException {
		logger.debug("del permissions id " + id);
		String sql = "select count(0) as ct from us_permissions up inner join us_role_permissions urp on urp.permissions_id=up.id where up.id=?";
		logger.debug(SqlUtil.Show(sql, id));
		Integer count = IntegerUtil.getInt0(jdbcTemplate.queryForList(sql, id).get(0).get("ct"));
		logger.debug("role count : " + count);
		if (count != 0) {
			throw new NotAllowDeleteException("该权限被分配[" + count + "]个角色，不能删除。");
		}
		sql = "update us_permissions set enable=0 where id=?";
		logger.debug(SqlUtil.Show(sql, id));
		jdbcTemplate.update(sql, id);
	}

	public List<PermissionsTreeBody> findPermissionsByParentID(HashMap<String, String> params) {
		Integer parentId = IntegerUtil.getInt(params.get("parentId"));
		Integer systemId = IntegerUtil.getInt(params.get("systemId"));
		if (parentId == null || systemId == null) {
			return new ArrayList<PermissionsTreeBody>();
		}
		String sql = "select * from us_permissions where enable=1 and parent_id=? and system_id=?";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, parentId, systemId);
		logger.debug(SqlUtil.Show(sql, parentId, systemId));
		List<PermissionsTreeBody> rst = null;
		try {
			rst = HMap.toList(list, PermissionsTreeBody.class);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (PermissionsTreeBody body : rst) {
			body.setState("closed");
			// fa-c 兼容easyui的自定义fa-c
			if (parentId == -1) {
				body.setIconCls("fa fa-pagelines fa-1x fa-c");
			} else {
				body.setIconCls("fa fa-pied-piper fa-1x fa-c");
			}
		}
		logger.debug(rst.toString());
		return rst;
	}

	public USPermissions findPermissionsById(HashMap<String, String> params) {
		String sql = "select * from us_permissions where id=? and enable=1";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, IntegerUtil.getInt0(params.get("id")));
		USPermissions permissions = new USPermissions();
		if (list.size() == 0) {
			return permissions;
		}
		Map<String, Object> map = list.get(0);
		permissions.setId(IntegerUtil.getInt0(map.get("id")));
		permissions.setSystemId(IntegerUtil.getInt0(map.get("system_id")));
		permissions.setParentId(IntegerUtil.getInt0(map.get("parent_id")));
		permissions.setPermissionsName(StringUtil.getStrEmpty(map.get("permissions_name")));
		permissions.setUrl(StringUtil.getStrEmpty(map.get("url")));
		permissions.setRemark(StringUtil.getStrEmpty(map.get("remark")));
		return permissions;
	}

}