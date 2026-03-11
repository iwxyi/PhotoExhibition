package com.photoexhibition.controller;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.FaceClusterDTO;
import com.photoexhibition.dto.PersonDTO;
import com.photoexhibition.dto.PersonListItemDTO;
import com.photoexhibition.dto.PersonSimilarityDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.AlbumRecommendationDTO;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import com.photoexhibition.service.FaceService;
import com.photoexhibition.service.PhotoService;
import com.photoexhibition.dto.PhotoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@Slf4j
public class FaceController {


    private final FaceService faceService;
    private final PersonProfileRepository personProfileRepository;
    private final com.photoexhibition.repository.PhotoAssignmentRepository photoAssignmentRepository;
    private final PhotoService photoService;
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "confidence") String sort,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) Integer clusterIndex) {
        if (personId != null) {
            // 返回与指定人物相似的未分配人脸，按相似度排序
            return ResponseEntity.ok(faceService.listUnassignedFacesForPerson(personId, PageRequest.of(page, size)));
        } else if (clusterIndex != null) {
            // 返回与指定聚类相似的未分配人脸，按相似度排序
            return ResponseEntity.ok(faceService.listUnassignedFacesForCluster(clusterIndex, PageRequest.of(page, size)));
        } else {
            // 返回全局未分配人脸，按指定排序
            return ResponseEntity.ok(faceService.listUnassignedFaces(PageRequest.of(page, size), sort));
        }
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
    public ResponseEntity<Page<com.photoexhibition.dto.PersonSummaryDTO>> listPersonsWithSample(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listPersonsWithSample(PageRequest.of(page, size)));
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
     * 根据人脸ID获取照片
     */
    @GetMapping("/faces/{faceId}/photos")
    public ResponseEntity<List<PhotoDTO>> getPhotosByFaceId(@PathVariable Long faceId) {
        return ResponseEntity.ok(faceService.getPhotosByFaceId(faceId));
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
     * 搜索人物（模糊匹配名称）
     */
    @GetMapping("/persons/search")
    public ResponseEntity<List<PersonSummaryDTO>> searchPersons(@RequestParam String q) {
        return ResponseEntity.ok(faceService.searchPersons(q));
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
     * 设置人物的样例照片
     * payload: { faceId: 123 }
     */
    @PostMapping("/persons/{personId}/set-sample")
    public ResponseEntity<PersonDTO> setPersonSamplePhoto(@PathVariable Long personId, @RequestBody Map<String, Object> payload) {
        Object faceIdObj = payload.get("faceId");
        if (faceIdObj == null) {
            return ResponseEntity.badRequest().body(null);
        }
        Long faceId;
        if (faceIdObj instanceof Number) {
            faceId = ((Number) faceIdObj).longValue();
        } else if (faceIdObj instanceof String) {
            faceId = Long.parseLong((String) faceIdObj);
        } else {
            return ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.ok(faceService.setPersonSamplePhoto(personId, faceId));
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
            @RequestParam(required = false) Double threshold,
            @RequestParam(required = false) Integer clusterPage,
            @RequestParam(required = false) Integer clusterSize) {
        double t = threshold != null ? threshold : clusteringDefaultThreshold;
        int page = clusterPage != null ? clusterPage : 0;
        int size = clusterSize != null ? clusterSize : Integer.MAX_VALUE; // 默认返回所有
        return ResponseEntity.ok(faceService.listPersonItems(t, page, size));
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
     * 获取聚类相似的已确认人物
     * 注意：此方法使用聚类算法返回的人脸（经过过滤），与手动搜索时的逻辑可能略有不同
     */
    @GetMapping("/clusters/{clusterIndex}/similar-persons")
    public ResponseEntity<List<PersonSimilarityDTO>> getSimilarPersonsForCluster(
            @PathVariable int clusterIndex,
            @RequestParam(defaultValue = "0.7") double clusterThreshold,
            @RequestParam(defaultValue = "0.1") double recommendThreshold) {
        List<PersonSimilarityDTO> result = faceService.getSimilarPersonsForCluster(clusterIndex, clusterThreshold, recommendThreshold);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取人物的套图推荐相册列表
     */
    @GetMapping("/persons/{personId}/album-recommendations")
    public ResponseEntity<List<AlbumRecommendationDTO>> getAlbumRecommendationsForPerson(@PathVariable Long personId) {
        List<AlbumRecommendationDTO> result = faceService.getAlbumRecommendationsForPerson(personId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取某人物被指派的图片（图片级别的认领）
     */
    @GetMapping("/persons/{personId}/assigned-photos")
    public ResponseEntity<org.springframework.data.domain.Page<PhotoDTO>> getAssignedPhotos(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.listPhotosAssignedToPerson(personId, PageRequest.of(page, size)));
    }

    /**
     * 将图片指派给人物（非人脸绑定）
     */
    @PostMapping("/photos/{photoId}/assign-person")
    public ResponseEntity<PhotoDTO> assignPhotoToPerson(@PathVariable Long photoId, @RequestParam Long personId) {
        return ResponseEntity.ok(photoService.assignPhotoToPerson(photoId, personId));
    }

    /**
     * 取消图片指派
     */
    @DeleteMapping("/photos/{photoId}/assign-person")
    public ResponseEntity<Void> unassignPhoto(@PathVariable Long photoId) {
        photoService.unassignPhoto(photoId);
        return ResponseEntity.ok().build();
    }

    /**
     * 检查图片的指派状态（调试用）
     */
    @GetMapping("/photos/{photoId}/assignment-status")
    public ResponseEntity<java.util.Map<String, Object>> getPhotoAssignmentStatus(@PathVariable Long photoId) {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("photoId", photoId);

        // 检查数据库中所有的 PhotoAssignment 记录
        java.util.List<com.photoexhibition.entity.PhotoAssignment> allAssignments = photoAssignmentRepository.findAll();
        status.put("totalAssignments", allAssignments.size());

        // 检查特定图片的指派
        java.util.Optional<com.photoexhibition.entity.PhotoAssignment> pa = photoAssignmentRepository.findByPhotoId(photoId);
        status.put("assignmentExists", pa.isPresent());
        status.put("queryMethod", "findByPhotoId");

        // 尝试其他查询方式
        java.util.List<com.photoexhibition.entity.PhotoAssignment> byPersonId = photoAssignmentRepository.findByPersonId(null); // 这不会工作，但让我们看看
        java.util.Optional<com.photoexhibition.entity.PhotoAssignment> byPhoto = photoAssignmentRepository.findAll().stream()
            .filter(a -> a.getPhotoId() != null && a.getPhotoId().equals(photoId))
            .findFirst();
        status.put("foundByStream", byPhoto.isPresent());

        if (pa.isPresent()) {
            com.photoexhibition.entity.PhotoAssignment assignment = pa.get();
            status.put("personId", assignment.getPersonId());
            // 尝试获取人物名称
            try {
                java.util.Optional<com.photoexhibition.entity.PersonProfile> personOpt = personProfileRepository.findById(assignment.getPersonId());
                if (personOpt.isPresent()) {
                    status.put("personName", personOpt.get().getName());
                }
            } catch (Exception e) {
                status.put("personNameError", e.getMessage());
            }
        } else if (byPhoto.isPresent()) {
            com.photoexhibition.entity.PhotoAssignment assignment = byPhoto.get();
            status.put("personId", assignment.getPersonId());
            try {
                java.util.Optional<com.photoexhibition.entity.PersonProfile> personOpt = personProfileRepository.findById(assignment.getPersonId());
                if (personOpt.isPresent()) {
                    status.put("personName", personOpt.get().getName());
                }
            } catch (Exception e) {
                status.put("personNameError", e.getMessage());
            }
            status.put("foundByAlternative", true);
        }

        return ResponseEntity.ok(status);
    }

    /**
     * 检查数据库表状态（调试用）
     */
    @GetMapping("/debug/database-status")
    public ResponseEntity<java.util.Map<String, Object>> getDatabaseStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();

        try {
            // 检查 PhotoAssignment 表
            long photoAssignmentCount = photoAssignmentRepository.count();
            status.put("photoAssignmentCount", photoAssignmentCount);

            // 检查 PersonProfile 表
            long personCount = personProfileRepository.count();
            status.put("personCount", personCount);

            // 检查 Face 表中的记录数（使用 faceRepository）
            // long faceCount = faceService.countFaces();
            status.put("faceCount", "N/A");

            // 检查最近的 PhotoAssignment 记录
            java.util.List<com.photoexhibition.entity.PhotoAssignment> recentAssignments = photoAssignmentRepository.findAll();
            if (!recentAssignments.isEmpty()) {
                java.util.Map<String, Object> sampleAssignment = new java.util.HashMap<>();
                com.photoexhibition.entity.PhotoAssignment pa = recentAssignments.get(0);
                sampleAssignment.put("id", pa.getId());
                sampleAssignment.put("photoId", pa.getPhotoId());
                sampleAssignment.put("personId", pa.getPersonId());
                sampleAssignment.put("createdAt", pa.getCreatedAt());
                status.put("sampleAssignment", sampleAssignment);
            }

            status.put("status", "success");
        } catch (Exception e) {
            status.put("status", "error");
            status.put("error", e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * 获取指定相册中与人物相似的未分配人脸
     */
    @GetMapping("/persons/{personId}/albums/{albumId}/similar-faces")
    public ResponseEntity<List<FaceDTO>> getSimilarFacesForAlbum(
            @PathVariable Long personId,
            @PathVariable Long albumId) {
        List<FaceDTO> result = faceService.getSimilarFacesForAlbum(personId, albumId);
        return ResponseEntity.ok(result);
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
     * 切换人物的隐藏状态
     */
    @PostMapping("/persons/{id}/toggle-hidden")
    public ResponseEntity<Map<String, Object>> togglePersonHidden(@PathVariable Long id) {
        var person = faceService.togglePersonHidden(id);
        boolean hidden = person.getHidden() != null && person.getHidden();
        return ResponseEntity.ok(Map.of(
            "id", person.getId(),
            "hidden", hidden,
            "message", hidden ? "已隐藏" : "已取消隐藏"
        ));
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

    /**
     * 批量绑定人脸到人物（或解绑，当 personId 为空）
     * payload: { faceIds: [...], personId: 123, confirmed: true }
     */
    @PostMapping("/faces/batch-assign")
    public ResponseEntity<Map<String, String>> batchAssignFaces(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("faceIds");
        if (!(idsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "faceIds 必须是数组"));
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
                return ResponseEntity.badRequest().body(Map.of("error", "faceIds 必须是数字或字符串形式的ID"));
            }
        }

        Long personId = null;
        if (payload.get("personId") instanceof Number) {
            personId = ((Number) payload.get("personId")).longValue();
        } else if (payload.get("personId") instanceof String) {
            personId = Long.parseLong((String) payload.get("personId"));
        }

        Boolean confirmed = null;
        if (payload.get("confirmed") instanceof Boolean) {
            confirmed = (Boolean) payload.get("confirmed");
        }

        if (faceIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "人脸ID列表不能为空"));
        }

        log.info("批量绑定人脸调用 - personId: {}, confirmed: {}, 数量: {}", personId, confirmed, faceIds.size());
        int count = faceService.batchAssignFacesToPerson(faceIds, personId, confirmed);
        log.info("批量绑定人脸完成 - 为人物 {} 绑定了 {} 个人脸", personId, count);
        return ResponseEntity.ok(Map.of("message", "已批量绑定 " + count + " 个人脸"));
    }

    /**
     * 批量绑定人脸到人物（按名称，自动创建或合并）
     * payload: { faceIds: [...], personName: "xxx" }
     */
    @PostMapping("/faces/assign-to-person")
    public ResponseEntity<Map<String, Object>> assignFacesToPerson(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("faceIds");
        if (!(idsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "faceIds 必须是数组"));
        }
        List<?> rawIds = (List<?>) idsObj;
        List<Long> faceIds = new ArrayList<>();
        for (Object o : rawIds) {
            if (o == null) continue;
            if (o instanceof Number) {
                faceIds.add(((Number) o).longValue());
            } else if (o instanceof String) {
                faceIds.add(Long.parseLong((String) o));
            }
        }

        String personName = (String) payload.get("personName");
        if (personName == null || personName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "personName 不能为空"));
        }

        // 查找或创建人物
        PersonProfile person = personProfileRepository.findByName(personName.trim())
            .orElseGet(() -> {
                PersonProfile p = new PersonProfile();
                p.setName(personName.trim());
                return personProfileRepository.save(p);
            });

        // 绑定所有人脸到该人物
        int count = faceService.batchAssignFacesToPerson(faceIds, person.getId(), true);
        log.info("批量绑定人脸完成 - 为人物 {} (ID: {}) 绑定了 {} 个人脸", person.getName(), person.getId(), count);

        return ResponseEntity.ok(Map.of(
            "message", "已绑定 " + count + " 张人脸到人物 " + person.getName(),
            "personId", person.getId(),
            "personName", person.getName(),
            "count", count
        ));
    }

    /**
     * 批量为照片指派人物
     * payload: { photoIds: [...], personId: 123 }
     */
    /**
     * 计算选中人脸与所有人物的相似度
     * payload: { faceIds: [...] }
     * 返回: [{ personId: 123, personName: "xxx", similarity: 0.85 }, ...]
     */
    @PostMapping("/faces/calculate-similarity-to-persons")
    public ResponseEntity<List<PersonSimilarityDTO>> calculateSimilarityToPersons(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("faceIds");
        if (!(idsObj instanceof List)) {
            return ResponseEntity.badRequest().build();
        }
        List<?> rawIds = (List<?>) idsObj;
        List<Long> faceIds = new ArrayList<>();
        for (Object o : rawIds) {
            if (o == null) continue;
            if (o instanceof Number) {
                faceIds.add(((Number) o).longValue());
            } else if (o instanceof String) {
                faceIds.add(Long.parseLong((String) o));
            }
        }
        
        List<PersonSimilarityDTO> result = faceService.calculateSimilarityToPersons(faceIds);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/photos/batch-assign")
    public ResponseEntity<Map<String, String>> batchAssignPhotos(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("photoIds");
        if (!(idsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "photoIds 必须是数组"));
        }
        List<?> rawIds = (List<?>) idsObj;
        List<Long> photoIds = new ArrayList<>();
        for (Object o : rawIds) {
            if (o == null) continue;
            if (o instanceof Number) {
                photoIds.add(((Number) o).longValue());
            } else if (o instanceof String) {
                photoIds.add(Long.parseLong((String) o));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "photoIds 必须是数字或字符串形式的ID"));
            }
        }

        Long personId = null;
        if (payload.get("personId") instanceof Number) {
            personId = ((Number) payload.get("personId")).longValue();
        } else if (payload.get("personId") instanceof String) {
            personId = Long.parseLong((String) payload.get("personId"));
        }

        if (photoIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "照片ID列表不能为空"));
        }

        log.info("batchAssignPhotos called - personId: {}, count: {}", personId, photoIds.size());
        log.debug("batchAssignPhotos photoIds: {}", photoIds);
        for (Long pid : photoIds) {
            photoService.assignPhotoToPerson(pid, personId);
        }
        log.info("batchAssignPhotos completed - assigned {} photos to person {}", photoIds.size(), personId);
        return ResponseEntity.ok(Map.of("message", "已批量指派 " + photoIds.size() + " 张照片"));
    }

    /**
     * 批量解绑照片指派
     * payload: { photoIds: [...] }
     */
    @PostMapping("/photos/batch-unassign")
    public ResponseEntity<Map<String, String>> batchUnassignPhotos(@RequestBody Map<String, List<Long>> payload) {
        List<Long> photoIds = payload.get("photoIds");
        if (photoIds == null || photoIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "photoIds 列表不能为空"));
        }
        log.info("batchUnassignPhotos called - count: {}", photoIds.size());
        log.debug("batchUnassignPhotos photoIds: {}", photoIds);
        for (Long pid : photoIds) {
            photoService.unassignPhoto(pid);
        }
        log.info("batchUnassignPhotos completed - unassigned {} photos", photoIds.size());
        return ResponseEntity.ok(Map.of("message", "已批量解绑 " + photoIds.size() + " 张照片"));
    }

    /**
     * 测试创建 PhotoAssignment 记录（调试用）
     */
    @PostMapping("/debug/test-photo-assignment/{photoId}/{personId}")
    public ResponseEntity<java.util.Map<String, Object>> testCreatePhotoAssignment(@PathVariable Long photoId, @PathVariable Long personId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        try {
            com.photoexhibition.entity.PhotoAssignment pa = new com.photoexhibition.entity.PhotoAssignment();
            pa.setPhotoId(photoId);
            pa.setPersonId(personId);

            com.photoexhibition.entity.PhotoAssignment saved = photoAssignmentRepository.save(pa);
            result.put("success", true);
            result.put("savedId", saved.getId());
            result.put("message", "PhotoAssignment created successfully");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }

        return ResponseEntity.ok(result);
    }
}

