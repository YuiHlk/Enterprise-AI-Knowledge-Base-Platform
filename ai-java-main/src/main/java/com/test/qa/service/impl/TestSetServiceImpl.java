package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.qa.domain.QaTestSet;
import com.test.qa.mapper.QaTestSetMapper;
import com.test.qa.service.TestSetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 标准评测问题集服务实现
 */
@Slf4j
@Service
public class TestSetServiceImpl extends ServiceImpl<QaTestSetMapper, QaTestSet> implements TestSetService {

    @Override
    public Page<QaTestSet> pageQuery(int page, int size, String setName, String difficulty) {
        LambdaQueryWrapper<QaTestSet> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(setName)) {
            wrapper.eq(QaTestSet::getSetName, setName);
        }
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(QaTestSet::getDifficulty, difficulty);
        }
        wrapper.orderByAsc(QaTestSet::getSetName)
               .orderByAsc(QaTestSet::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<String> listSetNames() {
        LambdaQueryWrapper<QaTestSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(QaTestSet::getSetName)
               .groupBy(QaTestSet::getSetName)
               .orderByAsc(QaTestSet::getSetName);
        return list(wrapper).stream()
                .map(QaTestSet::getSetName)
                .collect(Collectors.toList());
    }

    @Override
    public List<QaTestSet> getQuestionsBySetName(String setName) {
        LambdaQueryWrapper<QaTestSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaTestSet::getSetName, setName)
               .eq(QaTestSet::getStatus, "ACTIVE")
               .orderByAsc(QaTestSet::getId);
        return list(wrapper);
    }

    @Override
    @Transactional
    public List<QaTestSet> batchCreate(List<QaTestSet> questions) {
        for (QaTestSet q : questions) {
            if (q.getStatus() == null) {
                q.setStatus("ACTIVE");
            }
            if (q.getDifficulty() == null) {
                q.setDifficulty("MEDIUM");
            }
            save(q);
        }
        return questions;
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        removeById(id);
    }
}
