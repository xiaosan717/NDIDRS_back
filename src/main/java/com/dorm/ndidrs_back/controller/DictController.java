package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.*;
import com.dorm.ndidrs_back.mapper.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dict")
public class DictController {

    private final SysCollegeMapper collegeMapper;
    private final SysClassMapper classMapper;
    private final SysBuildingMapper buildingMapper;
    private final DormRoomMapper dormRoomMapper;

    public DictController(SysCollegeMapper collegeMapper,
                          SysClassMapper classMapper,
                          SysBuildingMapper buildingMapper,
                          DormRoomMapper dormRoomMapper) {
        this.collegeMapper = collegeMapper;
        this.classMapper = classMapper;
        this.buildingMapper = buildingMapper;
        this.dormRoomMapper = dormRoomMapper;
    }

    @GetMapping("/colleges")
    public Result<List<SysCollege>> getColleges() {
        List<SysCollege> list = collegeMapper.selectList(
                new LambdaQueryWrapper<SysCollege>().orderByAsc(SysCollege::getSortOrder));
        return Result.success(list);
    }

    @GetMapping("/classes")
    public Result<List<SysClass>> getClasses(@RequestParam(required = false) String collegeName) {
        LambdaQueryWrapper<SysClass> wrapper = new LambdaQueryWrapper<>();
        if (collegeName != null && !collegeName.isEmpty()) {
            wrapper.eq(SysClass::getCollegeName, collegeName);
        }
        wrapper.orderByAsc(SysClass::getSortOrder);
        List<SysClass> list = classMapper.selectList(wrapper);
        return Result.success(list);
    }

    @GetMapping("/buildings")
    public Result<List<SysBuilding>> getBuildings() {
        List<SysBuilding> list = buildingMapper.selectList(
                new LambdaQueryWrapper<SysBuilding>().orderByAsc(SysBuilding::getSortOrder));
        return Result.success(list);
    }

    @GetMapping("/rooms")
    public Result<List<DormRoom>> getRooms(@RequestParam(required = false) String buildingName) {
        LambdaQueryWrapper<DormRoom> wrapper = new LambdaQueryWrapper<>();
        if (buildingName != null && !buildingName.isEmpty()) {
            wrapper.eq(DormRoom::getBuilding, buildingName);
        }
        wrapper.orderByAsc(DormRoom::getFloor, DormRoom::getRoomNumber);
        List<DormRoom> list = dormRoomMapper.selectList(wrapper);
        return Result.success(list);
    }
}
