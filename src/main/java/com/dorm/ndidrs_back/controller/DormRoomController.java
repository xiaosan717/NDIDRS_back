package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.service.DormRoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class DormRoomController {
    private final DormRoomService dormRoomService;

    public DormRoomController(DormRoomService dormRoomService) {
        this.dormRoomService = dormRoomService;
    }

    @GetMapping
    public Result<Page<DormRoom>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String building) {
        LambdaQueryWrapper<DormRoom> wrapper = new LambdaQueryWrapper<>();
        if (building != null) wrapper.eq(DormRoom::getBuilding, building);
        Page<DormRoom> page = dormRoomService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<DormRoom> getById(@PathVariable Long id) {
        return Result.success(dormRoomService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody DormRoom room) {
        dormRoomService.save(room);
        return Result.success("添加成功", null);
    }

    @PutMapping
    public Result<Void> update(@RequestBody DormRoom room) {
        dormRoomService.updateById(room);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormRoomService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/byBuilding/{building}")
    public Result<List<DormRoom>> getByBuilding(@PathVariable String building) {
        List<DormRoom> rooms = dormRoomService.list(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getBuilding, building));
        return Result.success(rooms);
    }
}