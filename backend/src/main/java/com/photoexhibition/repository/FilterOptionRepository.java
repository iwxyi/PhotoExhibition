package com.photoexhibition.repository;

import com.photoexhibition.entity.FilterOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FilterOptionRepository extends JpaRepository<FilterOption, Long> {

    List<FilterOption> findByOptionType(String optionType);

    Optional<FilterOption> findByOptionTypeAndOptionKey(String optionType, String optionKey);

    @Modifying
    @Query("DELETE FROM FilterOption WHERE optionType = :optionType")
    void deleteByOptionType(@Param("optionType") String optionType);

    @Query("SELECT fo FROM FilterOption fo WHERE fo.optionType = :optionType ORDER BY fo.optionKey")
    List<FilterOption> findByOptionTypeOrderByOptionKey(@Param("optionType") String optionType);
}
