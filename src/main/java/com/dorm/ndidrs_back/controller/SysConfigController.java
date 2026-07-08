package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.SysConfig;
import com.dorm.ndidrs_back.service.SysConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configs")
public class SysConfigController {
    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.success(sysConfigService.list());
    }

    @GetMapping("/{key}")
    public Result<SysConfig> getByKey(@PathVariable String key) {
        SysConfig config = sysConfigService.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysConfig config) {
        sysConfigService.save(config);
        return Result.success("添加成功", null);
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysConfig config) {
        sysConfigService.updateById(config);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.removeById(id);
        return Result.success("删除成功", null);
    }
}