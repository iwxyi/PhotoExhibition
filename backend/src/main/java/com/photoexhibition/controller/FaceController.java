package com.photoexhibition.controller;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.FaceClusterDTO;
import com.photoexhibition.dto.PersonDTO;
import com.photoexhibition.dto.PersonListItemDTO;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.service.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FaceController {

    private final FaceService faceService;
    private final PersonProfileRepository personProfileRepository;
    @Value("${face.clustering.default-threshold:0.7}")
    private double clusteringDefaultThreshold;

    /**
     * 人脸列表（分页）
     */
    @GetMapping("/faces")
    public ResponseEntity<Page<FaceDTO>> paginateFaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(faceService.listFaces(keyword, PageRequest.of(page, size)));
    }

    /**
     * 未分配人脸
     */
    @GetMapping("/faces/unassigned")
    public ResponseEntity<Page<FaceDTO>> unassignedFaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listUnassignedFaces(PageRequest.of(page, size)));
    }

    /**
     * 已分配人脸
     */
    @GetMapping("/faces/assigned")
    public ResponseEntity<Page<FaceDTO>> assignedFaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listAssignedFaces(PageRequest.of(page, size)));
    }

    /**
     * 人物列表（含代表头像）
     */
    @GetMapping("/persons/with-sample")
    public ResponseEntity<List<com.photoexhibition.dto.PersonSummaryDTO>> listPersonsWithSample() {
        return ResponseEntity.ok(faceService.listPersonsWithSample());
    }

    /**
     * 人物照片瀑布流（该人物的所有人脸所在的照片）
     */
    @GetMapping("/persons/{personId}/photos")
    public ResponseEntity<Page<FaceDTO>> personPhotos(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listPersonFaces(personId, PageRequest.of(page, size)));
    }

    /**
     * 相似人脸查询
     */
    @GetMapping("/faces/{faceId}/similar")
    public ResponseEntity<List<FaceDTO>> similarFaces(
            @PathVariable Long faceId,
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "0.6") double threshold) {
        return ResponseEntity.ok(faceService.findSimilarFaces(faceId, top, threshold));
    }

    /**
     * 绑定/解绑人脸到人物（personId 为空则解绑）
     * @param confirmed true=已确认，false=自动分配，null=保持原状态
     */
    @PutMapping("/faces/{faceId}/assign")
    public ResponseEntity<FaceDTO> assignFace(@PathVariable Long faceId,
                                              @RequestParam(required = false) Long personId,
                                              @RequestParam(required = false) Boolean confirmed) {
        return ResponseEntity.ok(faceService.assignFaceToPerson(faceId, personId, confirmed));
    }

    /**
     * 获取某张照片的人脸列表
     */
    @GetMapping("/photos/{photoId}/faces")
    public ResponseEntity<List<FaceDTO>> listFaces(@PathVariable Long photoId) {
        return ResponseEntity.ok(faceService.getFacesByPhotoDTO(photoId));
    }

    /**
     * 更新单个人脸的关联人物（名称为空则清除关联）
     */
    @PutMapping("/faces/{faceId}")
    public ResponseEntity<FaceDTO> updateFace(@PathVariable Long faceId, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");
        return ResponseEntity.ok(faceService.updateFacePerson(faceId, name, description));
    }

    /**
     * 获取人物数量
     */
    @GetMapping("/persons/count")
    public ResponseEntity<Long> countPersons() {
        long count = personProfileRepository.count();
        return ResponseEntity.ok(count);
    }

    /**
     * 获取人物列表
     */
    @GetMapping("/persons")
    public ResponseEntity<List<PersonDTO>> listPersons() {
        return ResponseEntity.ok(faceService.listPersons());
    }

    /**
     * 创建人物
     */
    @PostMapping("/persons")
    public ResponseEntity<PersonDTO> createPerson(@RequestBody PersonDTO payload) {
        return ResponseEntity.ok(faceService.createOrUpdatePerson(null, payload));
    }

    /**
     * 更新人物
     */
    @PutMapping("/persons/{id}")
    public ResponseEntity<PersonDTO> updatePerson(@PathVariable Long id, @RequestBody PersonDTO payload) {
        return ResponseEntity.ok(faceService.createOrUpdatePerson(id, payload));
    }

    /**
     * 简易接口：快速为人脸设置名称与说明
     */
    @PostMapping("/faces/{faceId}/label")
    public ResponseEntity<FaceDTO> labelFace(@PathVariable Long faceId, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");
        return ResponseEntity.ok(faceService.updateFacePerson(faceId, name, description));
    }

    /**
     * 自动聚合相似人脸
     */
    @GetMapping("/faces/clusters")
    public ResponseEntity<List<FaceClusterDTO>> clusterSimilarFaces(
            @RequestParam(defaultValue = "0.6") double threshold) {
        return ResponseEntity.ok(faceService.clusterSimilarFaces(threshold));
    }

    /**
     * 批量创建人物并绑定人脸
     */
    @PostMapping("/persons/from-faces")
    public ResponseEntity<PersonDTO> createPersonFromFaces(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("faceIds");
        if (!(idsObj instanceof List)) {
            throw new IllegalArgumentException("faceIds 必须是数组");
        }
        List<?> rawIds = (List<?>) idsObj;
        List<Long> faceIds = new ArrayList<>();
        for (Object o : rawIds) {
            if (o == null) continue;
            if (o instanceof Number) {
                faceIds.add(((Number) o).longValue());
            } else if (o instanceof String) {
                faceIds.add(Long.parseLong((String) o));
            } else {
                throw new IllegalArgumentException("faceIds 必须是数字或字符串形式的ID");
            }
        }
        String name = (String) payload.get("name");
        String description = (String) payload.get("description");
        return ResponseEntity.ok(faceService.createPersonFromFaces(faceIds, name, description));
    }

    /**
     * 获取统一的人物列表（包括已确认人物和未确认聚类）
     */
    @GetMapping("/persons/items")
    public ResponseEntity<List<PersonListItemDTO>> listPersonItems(
            @RequestParam(required = false) Double threshold) {
        double t = threshold != null ? threshold : clusteringDefaultThreshold;
        return ResponseEntity.ok(faceService.listPersonItems(t));
    }

    /**
     * 获取与指定人物相似但未分配的人脸
     */
    @GetMapping("/persons/{personId}/similar-unassigned")
    public ResponseEntity<List<FaceDTO>> similarUnassignedFaces(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "50") int top,
            @RequestParam(required = false) Double threshold) {
        double t = threshold != null ? threshold : clusteringDefaultThreshold;
        return ResponseEntity.ok(faceService.findSimilarUnassignedFaces(personId, top, t));
    }

    /**
     * 获取聚类中的人脸列表
     */
    @GetMapping("/clusters/{clusterIndex}/faces")
    public ResponseEntity<List<FaceDTO>> getClusterFaces(
            @PathVariable int clusterIndex,
            @RequestParam(defaultValue = "0.6") double threshold) {
        return ResponseEntity.ok(faceService.getClusterFaces(clusterIndex, threshold));
    }

    /**
     * 获取已确认的人脸
     */
    @GetMapping("/persons/{personId}/faces/confirmed")
    public ResponseEntity<Page<FaceDTO>> getConfirmedFaces(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(faceService.listConfirmedFaces(personId, PageRequest.of(page, size)));
    }

    /**
     * 获取自动分配的人脸
     */
    @GetMapping("/persons/{personId}/faces/auto-assigned")
    public ResponseEntity<Page<FaceDTO>> getAutoAssignedFaces(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(faceService.listAutoAssignedFaces(personId, PageRequest.of(page, size)));
    }

    /**
     * 获取套图推荐（同一文件夹的相似人脸）
     */
    @GetMapping("/persons/{personId}/faces/same-folder")
    public ResponseEntity<List<FaceDTO>> getSameFolderSimilarFaces(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "50") int top) {
        return ResponseEntity.ok(faceService.listSameFolderSimilarFaces(personId, top));
    }

    /**
     * 删除人物（会解除所有人脸的关联）
     */
    @DeleteMapping("/persons/{id}")
    public ResponseEntity<Map<String, String>> deletePerson(@PathVariable Long id) {
        faceService.deletePerson(id);
        return ResponseEntity.ok(Map.of("message", "人物已删除"));
    }

    /**
     * 检查移除已确认人脸后，哪些自动分配人脸需要清理
     */
    @GetMapping("/persons/{personId}/faces/to-cleanup")
    public ResponseEntity<List<Long>> findFacesToCleanup(
            @PathVariable Long personId,
            @RequestParam Long removedFaceId) {
        return ResponseEntity.ok(faceService.findFacesToCleanupAfterRemoval(personId, removedFaceId));
    }

    /**
     * 批量解绑人脸
     */
    @PostMapping("/faces/batch-unassign")
    public ResponseEntity<Map<String, String>> batchUnassignFaces(@RequestBody Map<String, List<Long>> payload) {
        List<Long> faceIds = payload.get("faceIds");
        if (faceIds == null || faceIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "人脸ID列表不能为空"));
        }
        faceService.unassignFaces(faceIds);
        return ResponseEntity.ok(Map.of("message", "已批量解绑 " + faceIds.size() + " 个人脸"));
    }
}

