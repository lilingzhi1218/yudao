package cn.iocoder.yudao.module.system.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.api.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.mq.producer.permission.PermissionProducer;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.SetUtils.asSet;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import({PermissionServiceImpl.class})
public class PermissionServiceTest extends BaseDbUnitTest {

    @Resource
    private PermissionServiceImpl permissionService;

    @Resource
    private RoleMenuMapper roleMenuMapper;
    @Resource
    private UserRoleMapper userRoleMapper;

    @MockBean
    private RoleService roleService;
    @MockBean
    private MenuService menuService;
    @MockBean
    private DeptService deptService;
    @MockBean
    private AdminUserService userService;

    @MockBean
    private PermissionProducer permissionProducer;

    @Test
    public void testInitLocalCacheForRoleMenu() {
        // mock ??????
        RoleMenuDO roleMenuDO01 = randomPojo(RoleMenuDO.class, o -> o.setRoleId(1L).setMenuId(10L));
        roleMenuMapper.insert(roleMenuDO01);
        RoleMenuDO roleMenuDO02 = randomPojo(RoleMenuDO.class, o -> o.setRoleId(1L).setMenuId(20L));
        roleMenuMapper.insert(roleMenuDO02);

        // ??????
        permissionService.initLocalCacheForRoleMenu();
        // ?????? roleMenuCache ??????
        assertEquals(1, permissionService.getRoleMenuCache().keySet().size());
        assertEquals(asList(10L, 20L), permissionService.getRoleMenuCache().get(1L));
        // ?????? menuRoleCache ??????
        assertEquals(2, permissionService.getMenuRoleCache().size());
        assertEquals(singletonList(1L), permissionService.getMenuRoleCache().get(10L));
        assertEquals(singletonList(1L), permissionService.getMenuRoleCache().get(20L));
    }

    @Test
    public void testInitLocalCacheForUserRole() {
        // mock ??????
        UserRoleDO userRoleDO01 = randomPojo(UserRoleDO.class, o -> o.setUserId(1L).setRoleId(10L));
        userRoleMapper.insert(userRoleDO01);
        UserRoleDO roleMenuDO02 = randomPojo(UserRoleDO.class, o -> o.setUserId(1L).setRoleId(20L));
        userRoleMapper.insert(roleMenuDO02);

        // ??????
        permissionService.initLocalCacheForUserRole();
        // ?????? roleMenuCache ??????
        assertEquals(1, permissionService.getUserRoleCache().size());
        assertEquals(asSet(10L, 20L), permissionService.getUserRoleCache().get(1L));
    }

    @Test
    public void testGetRoleMenuListFromCache_superAdmin() {
        // ????????????
        Collection<Long> roleIds = singletonList(100L);
        Collection<Integer> menuTypes = asList(2, 3);
        Collection<Integer> menusStatuses = asList(0, 1);
        // mock ??????
        List<RoleDO> roleList = singletonList(randomPojo(RoleDO.class, o -> o.setId(100L)));
        when(roleService.getRolesFromCache(eq(roleIds))).thenReturn(roleList);
        when(roleService.hasAnySuperAdmin(same(roleList))).thenReturn(true);
        List<MenuDO> menuList = randomPojoList(MenuDO.class);
        when(menuService.getMenuListFromCache(eq(menuTypes), eq(menusStatuses))).thenReturn(menuList);

        // ??????
        List<MenuDO> result = permissionService.getRoleMenuListFromCache(roleIds, menuTypes, menusStatuses);
        // ??????
        assertSame(menuList, result);
    }

    @Test
    public void testGetRoleMenuListFromCache_normal() {
        // ????????????
        Collection<Long> roleIds = asSet(100L, 200L);
        Collection<Integer> menuTypes = asList(2, 3);
        Collection<Integer> menusStatuses = asList(0, 1);
        // mock ??????
        Multimap<Long, Long> roleMenuCache = ImmutableMultimap.<Long, Long>builder().put(100L, 1000L)
                .put(200L, 2000L).put(200L, 2001L).build();
        permissionService.setRoleMenuCache(roleMenuCache);
        List<MenuDO> menuList = randomPojoList(MenuDO.class);
        when(menuService.getMenuListFromCache(eq(asList(1000L, 2000L, 2001L)), eq(menuTypes), eq(menusStatuses))).thenReturn(menuList);

        // ??????
        List<MenuDO> result = permissionService.getRoleMenuListFromCache(roleIds, menuTypes, menusStatuses);
        // ??????
        assertSame(menuList, result);
    }

    @Test
    public void testGetUserRoleIdsFromCache() {
        // ????????????
        Long userId = 1L;
        Collection<Integer> roleStatuses = singleton(CommonStatusEnum.ENABLE.getStatus());
        // mock ??????
        Map<Long, Set<Long>> userRoleCache = MapUtil.<Long, Set<Long>>builder()
                .put(1L, asSet(10L, 20L)).build();
        permissionService.setUserRoleCache(userRoleCache);
        RoleDO roleDO01 = randomPojo(RoleDO.class, o -> o.setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(10L))).thenReturn(roleDO01);
        RoleDO roleDO02 = randomPojo(RoleDO.class, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(20L))).thenReturn(roleDO02);

        // ??????
        Set<Long> roleIds = permissionService.getUserRoleIdsFromCache(userId, roleStatuses);
        // ??????
        assertEquals(asSet(10L), roleIds);
    }

    @Test
    public void testGetRoleMenuIds_superAdmin() {
        // ????????????
        Long roleId = 100L;
        // mock ??????
        when(roleService.hasAnySuperAdmin(eq(singleton(100L)))).thenReturn(true);
        List<MenuDO> menuList = singletonList(randomPojo(MenuDO.class).setId(1L));
        when(menuService.getMenus()).thenReturn(menuList);

        // ??????
        Set<Long> menuIds = permissionService.getRoleMenuIds(roleId);
        // ??????
        assertEquals(singleton(1L), menuIds);
    }

    @Test
    public void testGetRoleMenuIds_normal() {
        // ????????????
        Long roleId = 100L;
        // mock ??????
        RoleMenuDO roleMenu01 = randomPojo(RoleMenuDO.class).setRoleId(100L).setMenuId(1L);
        roleMenuMapper.insert(roleMenu01);
        RoleMenuDO roleMenu02 = randomPojo(RoleMenuDO.class).setRoleId(100L).setMenuId(2L);
        roleMenuMapper.insert(roleMenu02);

        // ??????
        Set<Long> menuIds = permissionService.getRoleMenuIds(roleId);
        // ??????
        assertEquals(asSet(1L, 2L), menuIds);
    }

    @Test
    public void testAssignRoleMenu() {
        // ????????????
        Long roleId = 1L;
        Set<Long> menuIds = asSet(200L, 300L);
        // mock ??????
        RoleMenuDO roleMenu01 = randomPojo(RoleMenuDO.class).setRoleId(1L).setMenuId(100L);
        roleMenuMapper.insert(roleMenu01);
        RoleMenuDO roleMenu02 = randomPojo(RoleMenuDO.class).setRoleId(1L).setMenuId(200L);
        roleMenuMapper.insert(roleMenu02);

        // ??????
        permissionService.assignRoleMenu(roleId, menuIds);
        // ??????
        List<RoleMenuDO> roleMenuList = roleMenuMapper.selectList();
        assertEquals(2, roleMenuList.size());
        assertEquals(1L, roleMenuList.get(0).getRoleId());
        assertEquals(200L, roleMenuList.get(0).getMenuId());
        assertEquals(1L, roleMenuList.get(1).getRoleId());
        assertEquals(300L, roleMenuList.get(1).getMenuId());
        verify(permissionProducer).sendRoleMenuRefreshMessage();
    }

    @Test
    public void testAssignUserRole() {
        // ????????????
        Long userId = 1L;
        Set<Long> roleIds = asSet(200L, 300L);
        // mock ??????
        UserRoleDO userRole01 = randomPojo(UserRoleDO.class).setUserId(1L).setRoleId(100L);
        userRoleMapper.insert(userRole01);
        UserRoleDO userRole02 = randomPojo(UserRoleDO.class).setUserId(1L).setRoleId(200L);
        userRoleMapper.insert(userRole02);

        // ??????
        permissionService.assignUserRole(userId, roleIds);
        // ??????
        List<UserRoleDO> userRoleDOList = userRoleMapper.selectList();
        assertEquals(2, userRoleDOList.size());
        assertEquals(1L, userRoleDOList.get(0).getUserId());
        assertEquals(200L, userRoleDOList.get(0).getRoleId());
        assertEquals(1L, userRoleDOList.get(1).getUserId());
        assertEquals(300L, userRoleDOList.get(1).getRoleId());
        verify(permissionProducer).sendUserRoleRefreshMessage();
    }

    @Test
    public void testGetUserRoleIdListByUserId() {
        // ????????????
        Long userId = 1L;
        // mock ??????
        UserRoleDO userRoleDO01 = randomPojo(UserRoleDO.class, o -> o.setUserId(1L).setRoleId(10L));
        userRoleMapper.insert(userRoleDO01);
        UserRoleDO roleMenuDO02 = randomPojo(UserRoleDO.class, o -> o.setUserId(1L).setRoleId(20L));
        userRoleMapper.insert(roleMenuDO02);

        // ??????
        Set<Long> result = permissionService.getUserRoleIdListByUserId(userId);
        // ??????
        assertEquals(asSet(10L, 20L), result);
    }

    @Test
    public void testGetUserRoleIdListByRoleIds() {
        // ????????????
        Collection<Long> roleIds = asSet(10L, 20L);
        // mock ??????
        UserRoleDO userRoleDO01 = randomPojo(UserRoleDO.class, o -> o.setUserId(1L).setRoleId(10L));
        userRoleMapper.insert(userRoleDO01);
        UserRoleDO roleMenuDO02 = randomPojo(UserRoleDO.class, o -> o.setUserId(2L).setRoleId(20L));
        userRoleMapper.insert(roleMenuDO02);

        // ??????
        Set<Long> result = permissionService.getUserRoleIdListByRoleIds(roleIds);
        // ??????
        assertEquals(asSet(1L, 2L), result);
    }

    @Test
    public void testAssignRoleDataScope() {
        // ????????????
        Long roleId = 1L;
        Integer dataScope = 2;
        Set<Long> dataScopeDeptIds = asSet(10L, 20L);

        // ??????
        permissionService.assignRoleDataScope(roleId, dataScope, dataScopeDeptIds);
        // ??????
        verify(roleService).updateRoleDataScope(eq(roleId), eq(dataScope), eq(dataScopeDeptIds));
    }

    @Test
    public void testProcessRoleDeleted() {
        // ????????????
        Long roleId = randomLongId();
        // mock ?????? UserRole
        UserRoleDO userRoleDO01 = randomPojo(UserRoleDO.class, o -> o.setRoleId(roleId)); // ?????????
        userRoleMapper.insert(userRoleDO01);
        UserRoleDO userRoleDO02 = randomPojo(UserRoleDO.class); // ????????????
        userRoleMapper.insert(userRoleDO02);
        // mock ?????? RoleMenu
        RoleMenuDO roleMenuDO01 = randomPojo(RoleMenuDO.class, o -> o.setRoleId(roleId)); // ?????????
        roleMenuMapper.insert(roleMenuDO01);
        RoleMenuDO roleMenuDO02 = randomPojo(RoleMenuDO.class); // ????????????
        roleMenuMapper.insert(roleMenuDO02);

        // ??????
        permissionService.processRoleDeleted(roleId);
        // ???????????? RoleMenuDO
        List<RoleMenuDO> dbRoleMenus = roleMenuMapper.selectList();
        assertEquals(1, dbRoleMenus.size());
        assertPojoEquals(dbRoleMenus.get(0), roleMenuDO02);
        // ???????????? UserRoleDO
        List<UserRoleDO> dbUserRoles = userRoleMapper.selectList();
        assertEquals(1, dbUserRoles.size());
        assertPojoEquals(dbUserRoles.get(0), userRoleDO02);
        // ????????????
        verify(permissionProducer).sendRoleMenuRefreshMessage();
        verify(permissionProducer).sendUserRoleRefreshMessage();
    }

    @Test
    public void testProcessMenuDeleted() {
        // ????????????
        Long menuId = randomLongId();
        // mock ??????
        RoleMenuDO roleMenuDO01 = randomPojo(RoleMenuDO.class, o -> o.setMenuId(menuId)); // ?????????
        roleMenuMapper.insert(roleMenuDO01);
        RoleMenuDO roleMenuDO02 = randomPojo(RoleMenuDO.class); // ????????????
        roleMenuMapper.insert(roleMenuDO02);

        // ??????
        permissionService.processMenuDeleted(menuId);
        // ????????????
        List<RoleMenuDO> dbRoleMenus = roleMenuMapper.selectList();
        assertEquals(1, dbRoleMenus.size());
        assertPojoEquals(dbRoleMenus.get(0), roleMenuDO02);
        // ????????????
        verify(permissionProducer).sendRoleMenuRefreshMessage();
    }

    @Test
    public void testProcessUserDeleted() {
        // ????????????
        Long userId = randomLongId();
        // mock ??????
        UserRoleDO userRoleDO01 = randomPojo(UserRoleDO.class, o -> o.setUserId(userId)); // ?????????
        userRoleMapper.insert(userRoleDO01);
        UserRoleDO userRoleDO02 = randomPojo(UserRoleDO.class); // ????????????
        userRoleMapper.insert(userRoleDO02);

        // ??????
        permissionService.processUserDeleted(userId);
        // ????????????
        List<UserRoleDO> dbUserRoles = userRoleMapper.selectList();
        assertEquals(1, dbUserRoles.size());
        assertPojoEquals(dbUserRoles.get(0), userRoleDO02);
        // ????????????
        verify(permissionProducer).sendUserRoleRefreshMessage();
    }

    @Test
    public void testHasAnyPermissions_superAdmin() {
        // ????????????
        Long userId = 1L;
        String[] roles = new String[]{"system:user:query", "system:user:create"};
        // mock ????????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(100L)).build());
        RoleDO role = randomPojo(RoleDO.class, o -> o.setId(100L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(100L))).thenReturn(role);
        // mock ????????????
        when(roleService.hasAnySuperAdmin(eq(asSet(100L)))).thenReturn(true);

        // ??????
        boolean has = permissionService.hasAnyPermissions(userId, roles);
        // ??????
        assertTrue(has);
    }

    @Test
    public void testHasAnyPermissions_normal() {
        // ????????????
        Long userId = 1L;
        String[] roles = new String[]{"system:user:query", "system:user:create"};
        // mock ????????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(100L)).build());
        RoleDO role = randomPojo(RoleDO.class, o -> o.setId(100L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(100L))).thenReturn(role);
        // mock ????????????
        MenuDO menu = randomPojo(MenuDO.class, o -> o.setId(1000L));
        when(menuService.getMenuListByPermissionFromCache(eq("system:user:create"))).thenReturn(singletonList(menu));
        permissionService.setMenuRoleCache(ImmutableMultimap.<Long, Long>builder().put(1000L, 100L).build());


        // ??????
        boolean has = permissionService.hasAnyPermissions(userId, roles);
        // ??????
        assertTrue(has);
    }

    @Test
    public void testHasAnyRoles_superAdmin() {
        // ????????????
        Long userId = 1L;
        String[] roles = new String[]{"yunai", "tudou"};
        // mock ????????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(100L)).build());
        RoleDO role = randomPojo(RoleDO.class, o -> o.setId(100L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(100L))).thenReturn(role);
        // mock ????????????
        when(roleService.hasAnySuperAdmin(eq(asSet(100L)))).thenReturn(true);

        // ??????
        boolean has = permissionService.hasAnyRoles(userId, roles);
        // ??????
        assertTrue(has);
    }

    @Test
    public void testHasAnyRoles_normal() {
        // ????????????
        Long userId = 1L;
        String[] roles = new String[]{"yunai", "tudou"};
        // mock ????????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(100L)).build());
        RoleDO role = randomPojo(RoleDO.class, o -> o.setId(100L).setCode("yunai")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRoleFromCache(eq(100L))).thenReturn(role);
        // mock ????????????
        when(roleService.getRolesFromCache(eq(asSet(100L)))).thenReturn(singletonList(role));

        // ??????
        boolean has = permissionService.hasAnyRoles(userId, roles);
        // ??????
        assertTrue(has);
    }

    @Test
    public void testGetDeptDataPermission_All() {
        // ????????????
        Long userId = 1L;
        // mock ?????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(2L)).build());
        // mock ?????????????????????
        RoleDO roleDO = randomPojo(RoleDO.class, o -> o.setDataScope(DataScopeEnum.ALL.getScope())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRolesFromCache(eq(singleton(2L)))).thenReturn(singletonList(roleDO));
        when(roleService.getRoleFromCache(eq(2L))).thenReturn(roleDO);

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
        // ??????
        assertTrue(result.getAll());
        assertFalse(result.getSelf());
        assertTrue(CollUtil.isEmpty(result.getDeptIds()));
    }

    @Test
    public void testGetDeptDataPermission_DeptCustom() {
        // ????????????
        Long userId = 1L;
        // mock ?????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(2L)).build());
        // mock ?????????????????????
        RoleDO roleDO = randomPojo(RoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_CUSTOM.getScope())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRolesFromCache(eq(singleton(2L)))).thenReturn(singletonList(roleDO));
        when(roleService.getRoleFromCache(eq(2L))).thenReturn(roleDO);
        // mock ???????????????
        when(userService.getUser(eq(1L))).thenReturn(new AdminUserDO().setDeptId(3L), null, null); // ???????????? null ???????????????????????????????????????

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(roleDO.getDataScopeDeptIds().size() + 1, result.getDeptIds().size());
        assertTrue(CollUtil.containsAll(result.getDeptIds(), roleDO.getDataScopeDeptIds()));
        assertTrue(CollUtil.contains(result.getDeptIds(), 3L));
    }

    @Test
    public void testGetDeptDataPermission_DeptOnly() {
        // ????????????
        Long userId = 1L;
        // mock ?????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(2L)).build());
        // mock ?????????????????????
        RoleDO roleDO = randomPojo(RoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_ONLY.getScope())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRolesFromCache(eq(singleton(2L)))).thenReturn(singletonList(roleDO));
        when(roleService.getRoleFromCache(eq(2L))).thenReturn(roleDO);
        // mock ???????????????
        when(userService.getUser(eq(1L))).thenReturn(new AdminUserDO().setDeptId(3L), null, null); // ???????????? null ???????????????????????????????????????

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(1, result.getDeptIds().size());
        assertTrue(CollUtil.contains(result.getDeptIds(), 3L));
    }

    @Test
    public void testGetDeptDataPermission_DeptAndChild() {
        // ????????????
        Long userId = 1L;
        // mock ?????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(2L)).build());
        // mock ?????????????????????
        RoleDO roleDO = randomPojo(RoleDO.class, o -> o.setDataScope(DataScopeEnum.DEPT_AND_CHILD.getScope())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRolesFromCache(eq(singleton(2L)))).thenReturn(singletonList(roleDO));
        when(roleService.getRoleFromCache(eq(2L))).thenReturn(roleDO);
        // mock ???????????????
        when(userService.getUser(eq(1L))).thenReturn(new AdminUserDO().setDeptId(3L), null, null); // ???????????? null ???????????????????????????????????????
        // mock ??????????????????
        DeptDO deptDO = randomPojo(DeptDO.class);
        when(deptService.getDeptsByParentIdFromCache(eq(3L), eq(true)))
                .thenReturn(singletonList(deptDO));

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
        // ??????
        assertFalse(result.getAll());
        assertFalse(result.getSelf());
        assertEquals(2, result.getDeptIds().size());
        assertTrue(CollUtil.contains(result.getDeptIds(), deptDO.getId()));
        assertTrue(CollUtil.contains(result.getDeptIds(), 3L));
    }

    @Test
    public void testGetDeptDataPermission_Self() {
        // ????????????
        Long userId = 1L;
        // mock ?????????????????????
        permissionService.setUserRoleCache(MapUtil.<Long, Set<Long>>builder().put(1L, asSet(2L)).build());
        // mock ?????????????????????
        RoleDO roleDO = randomPojo(RoleDO.class, o -> o.setDataScope(DataScopeEnum.SELF.getScope())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(roleService.getRolesFromCache(eq(singleton(2L)))).thenReturn(singletonList(roleDO));
        when(roleService.getRoleFromCache(eq(2L))).thenReturn(roleDO);

        // ??????
        DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
        // ??????
        assertFalse(result.getAll());
        assertTrue(result.getSelf());
        assertTrue(CollUtil.isEmpty(result.getDeptIds()));
    }

}
