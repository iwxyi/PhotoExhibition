package com.photoexhibition.controller;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.AlbumRecommendationDTO;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.service.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PublicFaceController {

    private final FaceService faceService;
    private final PersonProfileRepository personProfileRepository;

    /**
     * 获取人物列表（含代表头像）- 公开API
     */
    @GetMapping("/persons/with-sample")
    public ResponseEntity<Page<PersonSummaryDTO>> getPersonsWithSample(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listVisiblePersonsWithSample(PageRequest.of(page, size)));
    }

    /**
     * 获取单个人物信息 - 公开API
     */
    @GetMapping("/persons/{personId}")
    public ResponseEntity<PersonSummaryDTO> getPerson(@PathVariable Long personId) {
        PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));
        return ResponseEntity.ok(faceService.toSummaryDTO(person));
    }

    /**
     * 获取人物照片瀑布流（该人物的所有人脸所在的照片）- 公开API
     */
    @GetMapping("/persons/{personId}/photos")
    public ResponseEntity<Page<FaceDTO>> getPersonPhotos(
            @PathVariable Long personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(faceService.listPersonFaces(personId, PageRequest.of(page, size)));
    }

    /**
     * 获取人物的套图推荐相册列表 - 公开API
     */
    @GetMapping("/persons/{personId}/album-recommendations")
    public ResponseEntity<List<AlbumRecommendationDTO>> getPersonAlbumRecommendations(@PathVariable Long personId) {
        List<AlbumRecommendationDTO> result = faceService.getAlbumRecommendationsForPerson(personId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取人物的代表图片（用于列表显示）- 公开API
     * 逻辑：按相册时间倒序，每个相册取一张（点赞 > 评分 > 创建时间）
     */
    @GetMapping("/persons/{personId}/sample-photos")
    public ResponseEntity<List<FaceDTO>> getPersonSamplePhotos(@PathVariable Long personId) {
        // 使用新的封面逻辑
        List<FaceDTO> faces = faceService.getPersonSamplePhotos(personId);
        return ResponseEntity.ok(faces);
    }

    /**
     * 获取指定相册中的人物列表（按人脸数量倒序）- 公开API
     */
    @GetMapping("/albums/{albumId}/persons")
    public ResponseEntity<List<PersonSummaryDTO>> getPersonsInAlbum(@PathVariable Long albumId) {
        return ResponseEntity.ok(faceService.getPersonsInAlbum(albumId, true));
    }
}
