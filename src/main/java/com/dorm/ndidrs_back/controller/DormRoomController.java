package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class DormRoomController {
    private final DormRoomService dormRoomService;
    private final SysUserService userService;

    public DormRoomController(DormRoomService dormRoomService, SysUserService userService) {
        this.dormRoomService = dormRoomService;
        this.userService = userService;
    }

    @GetMapping
    public Result<Page<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) String building,
                                                   @RequestParam(required = false) String roomNumber) {
        LambdaQueryWrapper<DormRoom> wrapper = new LambdaQueryWrapper<>();
        if (building != null && !building.isEmpty()) wrapper.eq(DormRoom::getBuilding, building);
        if (roomNumber != null && !roomNumber.isEmpty()) wrapper.eq(DormRoom::getRoomNumber, roomNumber);
        wrapper.orderByAsc(DormRoom::getBuilding).orderByAsc(DormRoom::getFloor).orderByAsc(DormRoom::getRoomNumber);
        Page<DormRoom> page = dormRoomService.page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> records = page.getRecords().stream().map(room -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", room.getId());
            map.put("roomNumber", room.getRoomNumber());
            map.put("building", room.getBuilding());
            map.put("floor", room.getFloor());
            map.put("capacity", room.getCapacity());
            map.put("leaderId", room.getLeaderId());

            if (room.getLeaderId() != null) {
                SysUser leader = userService.getById(room.getLeaderId());
                map.put("leaderName", leader != null ? leader.getRealName() : "-");
            } else {
                map.put("leaderName", "-");
            }

            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(SysUser::getBuilding, room.getBuilding())
                    .eq(SysUser::getRoom, room.getRoomNumber())
                    .in(SysUser::getRole, List.of("STUDENT", "DORM_LEADER"));
            long currentCount = userService.count(studentWrapper);
            map.put("currentCount", currentCount);

            return map;
        }).toList();

        Page<Map<String, Object>> resultPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        resultPage.setRecords(records);
        return Result.success(resultPage);
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

    @PutMapping("/{id}/leader")
    public Result<Void> bindLeader(@PathVariable Long id, @RequestBody Map<String, Long> data) {
        Long leaderId = data.get("leaderId");
        DormRoom room = dormRoomService.getById(id);
        if (room == null) {
            return Result.error(400, "宿舍不存在");
        }
        room.setLeaderId(leaderId);
        dormRoomService.updateById(room);
        return Result.success("绑定成功", null);
    }

    @PostMapping("/import")
    public Result<Void> importRooms(@RequestParam("file") MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String building = getCellValueAsString(row.getCell(0));
                String roomNumber = getCellValueAsString(row.getCell(1));
                Integer floor = getCellValueAsInt(row.getCell(2));
                Integer capacity = getCellValueAsInt(row.getCell(3));

                if (building == null || roomNumber == null) continue;

                DormRoom existing = dormRoomService.getOne(new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuilding, building)
                        .eq(DormRoom::getRoomNumber, roomNumber));

                DormRoom room = existing != null ? existing : new DormRoom();
                room.setBuilding(building);
                room.setRoomNumber(roomNumber);
                room.setFloor(floor != null ? floor : 1);
                room.setCapacity(capacity != null ? capacity : 4);

                dormRoomService.saveOrUpdate(room);
            }
            return Result.success("导入成功", null);
        } catch (IOException e) {
            return Result.error(500, "导入失败：" + e.getMessage());
        }
    }

    @GetMapping("/export")
    public void exportRooms(HttpServletResponse response) throws IOException {
        List<DormRoom> rooms = dormRoomService.list();

        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = response.getOutputStream()) {

            Sheet sheet = workbook.createSheet("宿舍列表");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"宿舍号", "楼栋", "楼层", "容纳人数", "寝室长"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (DormRoom room : rooms) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(room.getRoomNumber());
                row.createCell(1).setCellValue(room.getBuilding());
                row.createCell(2).setCellValue(room.getFloor() != null ? room.getFloor() : 0);
                row.createCell(3).setCellValue(room.getCapacity() != null ? room.getCapacity() : 0);

                if (room.getLeaderId() != null) {
                    SysUser leader = userService.getById(room.getLeaderId());
                    row.createCell(4).setCellValue(leader != null ? leader.getRealName() : "-");
                } else {
                    row.createCell(4).setCellValue("-");
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=rooms.xlsx");
            workbook.write(outputStream);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private Integer getCellValueAsInt(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim();
                yield value.isEmpty() ? null : Integer.parseInt(value);
            }
            default -> null;
        };
    }
}