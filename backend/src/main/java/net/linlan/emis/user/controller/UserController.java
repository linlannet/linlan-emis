package net.linlan.emis.user.controller;

import net.linlan.emis.common.Result;
import net.linlan.emis.user.security.RequiresRole;
import net.linlan.emis.user.dto.UserInfoResponse;
import net.linlan.emis.user.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理（仅主任 roleLevel=0 可操作），改编自 dsh-java-ontology 的 SysUserController。
 */
@RestController
@RequestMapping("/api/auth/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequiresRole(0)
    @GetMapping
    public Result<List<UserInfoResponse>> list() {
        return Result.success(userService.findAll());
    }

    @RequiresRole(0)
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(userService.count());
    }

    @RequiresRole(0)
    @DeleteMapping("/{id}")
    public Result<Integer> delete(@PathVariable Long id) {
        int deleted = userService.softDelete(id);
        return Result.success("删除" + deleted + "条记录", deleted);
    }
}
