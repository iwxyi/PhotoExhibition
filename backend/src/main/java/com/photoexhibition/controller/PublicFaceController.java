package com.photoexhibition.controller;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.AlbumRecommendationDTO;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.service.FaceService;
import com.photoexhibition.service.AlbumService;
import com.photoexhibition.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PublicFaceController {

    private final FaceService faceService;
    private final PersonProfileRepository personProfileRepository;
    private final AlbumService albumService;
    private final PhotoService photoService;

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

    /**
     * 根据名称搜索人物（用于短链接）
     */
    @GetMapping("/persons/search")
    public ResponseEntity<PersonSummaryDTO> searchPersonByName(@RequestParam String name) {
        PersonSummaryDTO person = faceService.searchPersonByName(name);
        if (person == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(person);
    }

    /**
     * 通用搜索（搜索相册、人物、照片）
     * 返回匹配的结果列表，支持去重（根据照片ID去重）
     * 排序按设置的照片顺序
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> globalSearch(@RequestParam String q) {
        Map<String, Object> result = new HashMap<>();

        // 搜索相册
        List<AlbumDTO> albums = new ArrayList<>();
        try {
            List<Album> albumList = albumService.searchAlbumsByName(q);
            for (Album album : albumList) {
                if (albums.size() >= 10) break;
                albums.add(albumService.getAlbumById(album.getId()));
            }
        } catch (Exception e) {
            log.warn("搜索相册失败: {}", e.getMessage());
        }
        result.put("albums", albums);

        // 搜索人物
        List<PersonSummaryDTO> persons = new ArrayList<>();
        try {
            List<PersonProfile> personList = personProfileRepository.searchByNameList(q);
            for (PersonProfile p : personList) {
                if (persons.size() >= 10) break;
                if (p.getHidden() == null || !p.getHidden()) {
                    persons.add(faceService.toSummaryDTO(p));
                }
            }
        } catch (Exception e) {
            log.warn("搜索人物失败: {}", e.getMessage());
        }
        result.put("persons", persons);

        // 搜索照片（文件名）并按设置排序
        List<PhotoDTO> photos = new ArrayList<>();
        try {
            // 获取按设置排序的照片列表
            List<Photo> photoList = photoService.searchPhotosByKeyword(q);
            // 用于去重
            Set<Long> seenPhotoIds = new HashSet<>();
            for (Photo photo : photoList) {
                if (seenPhotoIds.contains(photo.getId())) {
                    continue; // 跳过重复
                }
                if (photos.size() >= 20) break;
                seenPhotoIds.add(photo.getId());
                // 只返回未隐藏的照片
                if (photo.getIsHidden() == null || !photo.getIsHidden()) {
                    photos.add(photoService.getPhotoById(photo.getId()));
                }
            }
        } catch (Exception e) {
            log.warn("搜索照片失败: {}", e.getMessage());
        }
        result.put("photos", photos);

        return ResponseEntity.ok(result);
    }
}
