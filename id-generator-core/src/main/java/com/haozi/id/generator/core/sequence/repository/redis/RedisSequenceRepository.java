package com.haozi.id.generator.core.sequence.repository.redis;

import com.haozi.id.generator.core.sequence.repository.ISequenceRepository;
import com.haozi.id.generator.core.sequence.repository.SequenceEnum;
import com.haozi.id.generator.core.sequence.repository.SequenceRuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 持久化-Redis方式
 * <p>
 * TODO 升级lua脚本方式一次操作
 *
 * @author haozi
 * @date 2020/4/262:40 下午
 */
@Slf4j
public class RedisSequenceRepository implements ISequenceRepository {
    private final static String SEQUENCE_RULE_ID_KEY = "sequence:rule:id";

    private final static String SEQUENCE_RULE_DATA_KEY = "sequence:rule:data";

    private final static String SEQUENCE_KEY = "sequence:key:";

    private RedisTemplate<String, Object> redisTemplate;

    public RedisSequenceRepository(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Integer insertRule(SequenceRuleDefinition sequenceRule) {
        String key = sequenceRule.getKey();
        if (redisTemplate.opsForHash().hasKey(SEQUENCE_RULE_DATA_KEY, key)) {
            throw new IllegalArgumentException("SequenceRuleDefinition key [" + key + "] is exists");
        }
        Long increment = redisTemplate.opsForValue().increment(SEQUENCE_RULE_ID_KEY);
        sequenceRule.setId(increment);
        redisTemplate.opsForHash().put(SEQUENCE_RULE_DATA_KEY, sequenceRule.getKey(), sequenceRule);
        return 1;
    }

    @Override
    public Integer updateRuleByKey(SequenceRuleDefinition sequenceRule) {
        redisTemplate.opsForHash().put(SEQUENCE_RULE_DATA_KEY, sequenceRule.getKey(), sequenceRule);
        return 1;
    }

    @Override
    public SequenceRuleDefinition getRuleByKey(String key) {
        return (SequenceRuleDefinition) redisTemplate.opsForHash().get(SEQUENCE_RULE_DATA_KEY, key);
    }

    @Override
    public List<SequenceRuleDefinition> getRuleByStatus(SequenceEnum.Status status) {
        List<Object> values = redisTemplate.opsForHash().values(SEQUENCE_RULE_DATA_KEY);
        /**
         * 没排序
         */
        return values.stream()
                .map(obj -> (SequenceRuleDefinition) obj)
                .filter(rule -> status.getValue().equals(rule.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * @param key      精确匹配
     * @param status
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public List<SequenceRuleDefinition> getRuleByPage(String key, SequenceEnum.Status status, int page, int pageSize) {
        if (key != null) {
            SequenceRuleDefinition rule = (SequenceRuleDefinition) redisTemplate.opsForHash().get(SEQUENCE_RULE_DATA_KEY, key);
            return Collections.singletonList(rule);
        }
        List<Object> values = redisTemplate.opsForHash().values(SEQUENCE_RULE_DATA_KEY);

        Stream<SequenceRuleDefinition> sequenceRuleDefinitionStream = values.stream()
                .map(obj -> (SequenceRuleDefinition) obj);
        if (status != null) {
            sequenceRuleDefinitionStream = sequenceRuleDefinitionStream.filter(rule -> status.getValue().equals(rule.getStatus()));
        }

        List<SequenceRuleDefinition> collect = sequenceRuleDefinitionStream.collect(Collectors.toList());
        int fromIndex = pageSize * (page - 1);
        int toIndex = fromIndex + pageSize;
        return collect.subList(fromIndex, toIndex);
    }

    @Override
    public Long getRuleCount(String key, SequenceEnum.Status status) {
        if (key != null) {
            if (redisTemplate.opsForHash().hasKey(SEQUENCE_RULE_DATA_KEY, key)) {
                return 1L;
            }
            return 0L;
        }
        List<Object> values = redisTemplate.opsForHash().values(SEQUENCE_RULE_DATA_KEY);

        Stream<SequenceRuleDefinition> sequenceRuleDefinitionStream = values.stream()
                .map(obj -> (SequenceRuleDefinition) obj);
        if (status != null) {
            sequenceRuleDefinitionStream = sequenceRuleDefinitionStream.filter(rule -> status.getValue().equals(rule.getStatus()));
        }
        return sequenceRuleDefinitionStream.count();
    }

    @Override
    public Long incAndGetOffset(String sequenceKey, long inc) {
        return redisTemplate.opsForValue().increment(SEQUENCE_KEY + sequenceKey, inc);
    }

    @Override
    public Integer updateOffset(String sequenceKey, long offset) {
        redisTemplate.opsForValue().set(SEQUENCE_KEY + sequenceKey, offset);
        return 1;
    }

    @Override
    public Long getSequenceOffset(String sequenceKey) {
        return (Long) redisTemplate.opsForValue().get(SEQUENCE_KEY + sequenceKey);
    }
}
