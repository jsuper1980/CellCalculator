package j2.basic.utils.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单元格计算引擎
 * 
 * @author j2
 * @version 1.2
 */
public class CellCalculator {

    // ==================== 核心数据结构 ====================

    /**
     * 单元格存储 - 使用ConcurrentHashMap保证线程安全
     * 键：单元格ID，值：Cell对象
     */
    private final ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();

    /**
     * 依赖关系图 - 记录单元格间的依赖关系
     * 键：被依赖的单元格ID，值：依赖它的单元格ID集合
     * 使用ConcurrentHashMap.newKeySet()创建线程安全的Set
     */
    private final ConcurrentHashMap<String, Set<String>> dependents = new ConcurrentHashMap<>();

    /**
     * 使用StampedLock替代ReadWriteLock，提供更好的并发性能
     * StampedLock支持乐观读锁，在读多写少的场景下性能更优
     */
    private final StampedLock lock = new StampedLock();

    /**
     * 线程池 - 用于并行计算依赖单元格
     * 根据CPU核心数动态调整线程池大小，避免过度创建线程
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));

    // ==================== 常量定义 ====================

    /**
     * 数学计算上下文 - 保证高精度计算
     * 精度34位，使用HALF_UP舍入模式
     */
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    /**
     * 内置函数名集合 - 用于验证单元格ID时排除函数名
     * 使用不可变Set提高性能和安全性
     */
    private static final Set<String> BUILT_IN_FUNCTIONS = Set.of(
            "sqrt", "abs", "ceil", "floor", "round", "sin", "cos", "tan",
            "asin", "acos", "atan", "sinh", "cosh", "tanh", "log", "log10",
            "exp", "pow", "min", "max", "avg", "and", "or", "not", "xor",
            "if", "ifs", "jcall");

    /**
     * 单元格ID验证正则表达式
     * 匹配以字母或下划线开头，后跟字母、数字或下划线的标识符
     */
    private static final Pattern CELL_ID_PATTERN = Pattern.compile(
            "^[\\p{L}_][\\p{L}\\p{N}_]*$");

    /**
     * 单元格引用提取正则表达式
     * 用于从公式中提取单元格引用
     */
    private static final Pattern CELL_REFERENCE_PATTERN = Pattern.compile(
            "\\b([\\p{L}_][\\p{L}\\p{N}_]*)\\b");

    // ==================== 公共API方法 ====================

    /**
     * 设置单元格内容 - 核心方法
     * 
     * 执行流程：
     * 1. 验证单元格ID有效性
     * 2. 获取写锁保证线程安全
     * 3. 解析依赖关系并检查循环引用
     * 4. 更新依赖关系图
     * 5. 计算单元格值
     * 6. 重新计算依赖此单元格的其他单元格
     * 
     * @param cellId 单元格标识，必须符合命名规范
     * @param content 单元格内容，可以是数值、字符串或公式
     * @throws IllegalArgumentException 当单元格ID无效时
     * @throws RuntimeException 当检测到循环引用时
     */
    public void set(String cellId, String content) {
        // 1. 输入验证
        validateCellId(cellId);

        // 2. 获取写锁 - 使用StampedLock的写锁
        long stamp = lock.writeLock();
        try {
            // 3. 获取或创建单元格对象
            Cell cell = cells.computeIfAbsent(cellId, Cell::new);
            cell.setDefine(content);
            cell.setError(null); // 清除之前的错误信息

            // 4. 解析新的依赖关系
            Set<String> oldDependencies = cell.getDependencies();
            Set<String> newDependencies = extractDependencies(content);

            // 5. 循环引用检测 - 在更新依赖关系前检查
            checkCircularReference(cellId, newDependencies);

            // 6. 更新依赖关系图
            updateDependencies(cellId, oldDependencies, newDependencies);
            cell.setDependencies(newDependencies);

            // 7. 计算当前单元格值
            calculateCell(cell);

            // 8. 重新计算所有依赖此单元格的其他单元格
            recalculateDependents(cellId);

        } finally {
            // 9. 释放写锁
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 统一的数值设置方法 - 减少代码重复
     * 将各种数值类型转换为字符串后调用主设置方法
     * 
     * @param cellId 单元格标识
     * @param value 数值（支持Number及其子类、基本数值类型、布尔值）
     */
    public void set(String cellId, Object value) {
        if (value == null) {
            set(cellId, "");
        } else {
            set(cellId, value.toString());
        }
    }


    /**
     * 获取单元格值用于表达式计算
     */
    Object getCellValue(String cellId) {
        // 1. 尝试乐观读锁
        long stamp = lock.tryOptimisticRead();
        Cell cell = cells.get(cellId);

        // 2. 验证乐观读是否有效
        if (!lock.validate(stamp)) {
            // 3. 乐观读失败，使用悲观读锁
            stamp = lock.readLock();
            try {
                cell = cells.get(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        // 4. 处理单元格不存在或有错误的情况
        if (cell == null || cell.hasError()) {
            return null;
        }

        // 5. 格式化并返回值
        return cell.getValue();
    }

    /**
     * 获取单元格字符串值
     * 
     * 使用乐观读锁提高并发性能：
     * 1. 先尝试乐观读
     * 2. 如果数据在读取过程中被修改，则升级为悲观读锁
     * 
     * @param cellId 单元格标识
     * @return 单元格值的字符串表示，错误或不存在时返回null
     */
    public String get(String cellId) {
        return formatCellValue(getCellValue(cellId));
    }

    /**
     * 获取单元格数值
     * 
     * @param cellId 单元格标识
     * @return 单元格的BigDecimal值，非数值或错误时返回null
     */
    public BigDecimal getNumber(String cellId) {
        long stamp = lock.tryOptimisticRead();
        Cell cell = cells.get(cellId);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                cell = cells.get(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        if (cell == null || cell.hasError()) {
            return null;
        }

        Object value = cell.getValue();
        return (value instanceof BigDecimal) ? (BigDecimal) value : null;
    }

    /**
     * 获取单元格原始定义
     * 
     * @param cellId 单元格标识
     * @return 单元格的原始定义字符串
     */
    public String getDefine(String cellId) {
        long stamp = lock.tryOptimisticRead();
        Cell cell = cells.get(cellId);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                cell = cells.get(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return cell != null ? cell.getDefine() : null;
    }

    /**
     * 获取单元格错误信息
     * 
     * @param cellId 单元格标识
     * @return 错误信息，无错误时返回null
     */
    public String getError(String cellId) {
        long stamp = lock.tryOptimisticRead();
        Cell cell = cells.get(cellId);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                cell = cells.get(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return cell != null ? cell.getError() : null;
    }

    /**
     * 获取单元格值类型
     * 
     * @param cellId 单元格标识
     * @return 单元格值的类型字符串
     */
    public String getType(String cellId) {
        long stamp = lock.tryOptimisticRead();
        Cell cell = cells.get(cellId);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                cell = cells.get(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return cell != null ? cell.getValueType() : null;
    }

    /**
     * 删除单元格
     * 
     * 执行步骤：
     * 1. 获取写锁
     * 2. 移除单元格
     * 3. 清理依赖关系
     * 4. 重新计算受影响的单元格
     * 
     * @param cellId 单元格标识
     */
    public void del(String cellId) {
        long stamp = lock.writeLock();
        try {
            Cell cell = cells.remove(cellId);
            if (cell != null) {
                // 清理依赖关系：移除此单元格的所有依赖
                updateDependencies(cellId, cell.getDependencies(), Collections.emptySet());
                // 移除其他单元格对此单元格的依赖记录
                dependents.remove(cellId);

                // 重新计算依赖此单元格的其他单元格
                recalculateDependents(cellId);
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 检查单元格是否存在
     * 
     * @param cellId 单元格标识
     * @return 是否存在
     */
    public boolean exist(String cellId) {
        long stamp = lock.tryOptimisticRead();
        boolean exists = cells.containsKey(cellId);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                exists = cells.containsKey(cellId);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return exists;
    }

    /**
     * 关闭计算引擎，释放资源
     * 
     * 优雅关闭线程池：
     * 1. 调用shutdown()停止接受新任务
     * 2. 等待现有任务完成（最多5秒）
     * 3. 强制关闭未完成的任务
     * 4. 处理中断异常
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 验证单元格ID的有效性
     * 
     * 验证规则：
     * 1. 不能为null或空字符串
     * 2. 必须符合标识符命名规范
     * 3. 不能与内置函数名冲突
     * 
     * @param cellId 待验证的单元格ID
     * @throws IllegalArgumentException 当ID无效时抛出异常
     */
    private void validateCellId(String cellId) {
        if (cellId == null || cellId.trim().isEmpty()) {
            throw new IllegalArgumentException("单元格ID不能为空");
        }

        if (!CELL_ID_PATTERN.matcher(cellId).matches()) {
            throw new IllegalArgumentException("无效的单元格ID: " + cellId +
                    " (必须以字母或下划线开头，后跟字母、数字或下划线)");
        }

        if (BUILT_IN_FUNCTIONS.contains(cellId.toLowerCase())) {
            throw new IllegalArgumentException("单元格ID不能使用内置函数名: " + cellId);
        }
    }

    /**
     * 从单元格内容中提取依赖关系
     * 
     * 解析逻辑：
     * 1. 只处理以"="开头的公式
     * 2. 使用正则表达式匹配标识符
     * 3. 排除内置函数名
     * 4. 验证是否为有效的单元格ID
     * 
     * @param content 单元格内容
     * @return 依赖的单元格ID集合
     */
    private Set<String> extractDependencies(String content) {
        // 使用HashSet避免重复依赖
        Set<String> dependencies = new HashSet<>();

        // 只处理公式（以"="开头）
        if (content == null || !content.startsWith("=")) {
            return dependencies;
        }

        // 提取公式部分（去掉"="）
        String formula = content.substring(1);
        Matcher matcher = CELL_REFERENCE_PATTERN.matcher(formula);

        // 查找所有匹配的标识符
        while (matcher.find()) {
            String reference = matcher.group(1);

            // 排除内置函数名和无效的单元格ID
            if (!BUILT_IN_FUNCTIONS.contains(reference.toLowerCase()) &&
                    CELL_ID_PATTERN.matcher(reference).matches()) {
                dependencies.add(reference);
            }
        }

        return dependencies;
    }

    /**
     * 检查是否存在循环引用
     * 
     * 使用深度优先搜索（DFS）检测循环：
     * 1. 维护访问状态集合
     * 2. 递归检查每个依赖
     * 3. 发现循环时立即抛出异常
     * 
     * @param cellId 当前单元格ID
     * @param newDependencies 新的依赖关系
     * @throws RuntimeException 当检测到循环引用时
     */
    private void checkCircularReference(String cellId, Set<String> newDependencies) {
        Set<String> visited = new HashSet<>();
        if (hasCircularReference(cellId, newDependencies, visited)) {
            throw new RuntimeException("检测到循环引用: " + cellId +
                    " -> " + String.join(" -> ", visited));
        }
    }

    /**
     * 递归检查循环引用
     * 
     * @param cellId 当前检查的单元格ID
     * @param dependencies 当前单元格的依赖
     * @param visited 已访问的单元格集合
     * @return 是否存在循环引用
     */
    private boolean hasCircularReference(String cellId, Set<String> dependencies, Set<String> visited) {
        // 如果当前单元格已在访问路径中，说明存在循环
        if (visited.contains(cellId)) {
            return true;
        }

        // 将当前单元格加入访问路径
        visited.add(cellId);

        // 递归检查每个依赖
        for (String dep : dependencies) {
            Cell depCell = cells.get(dep);
            if (depCell != null) {
                if (hasCircularReference(dep, depCell.getDependencies(), visited)) {
                    return true;
                }
            }
        }

        // 回溯：从访问路径中移除当前单元格
        visited.remove(cellId);
        return false;
    }

    /**
     * 更新依赖关系图
     * 
     * 维护双向依赖关系：
     * 1. 移除旧的依赖关系
     * 2. 添加新的依赖关系
     * 3. 自动清理空的依赖集合
     * 
     * @param cellId 单元格ID
     * @param oldDeps 旧的依赖关系
     * @param newDeps 新的依赖关系
     */
    private void updateDependencies(String cellId, Set<String> oldDeps, Set<String> newDeps) {
        // 移除旧的依赖关系
        if (oldDeps != null) {
            for (String oldDep : oldDeps) {
                dependents.computeIfPresent(oldDep, (k, v) -> {
                    v.remove(cellId);
                    // 如果集合为空，返回null以移除该条目
                    return v.isEmpty() ? null : v;
                });
            }
        }

        // 添加新的依赖关系
        if (newDeps != null) {
            for (String newDep : newDeps) {
                // 使用computeIfAbsent确保线程安全地创建Set
                dependents.computeIfAbsent(newDep, k -> ConcurrentHashMap.newKeySet()).add(cellId);
            }
        }
    }

    /**
     * 计算单元格值
     * 
     * 支持的内容类型：
     * 1. 公式（以"="开头）
     * 2. 字符串字面量（以单引号包围）
     * 3. 数值和布尔值
     * 4. 普通字符串
     * 
     * @param cell 待计算的单元格
     */
    private void calculateCell(Cell cell) {
        try {
            String define = cell.getDefine();
            if (define == null || define.isEmpty()) {
                cell.setValue(null);
                return;
            }

            if (define.startsWith("=")) {
                // 公式计算
                String formula = define.substring(1);
                Object result = evaluateExpression(formula);
                cell.setValue(result);
            } else if (define.startsWith("'") && define.endsWith("'") && define.length() > 1) {
                // 字符串字面量：去掉单引号
                cell.setValue(define.substring(1, define.length() - 1));
            } else {
                // 尝试解析为数值、布尔值或字符串
                Object value = parseValue(define);
                cell.setValue(value);
            }
        } catch (Exception e) {
            // 计算出错时记录错误信息，值设为null
            cell.setError(e.getMessage());
            cell.setValue(null);
        }
    }

    /**
     * 解析字符串值为适当的数据类型
     * 
     * 解析优先级：
     * 1. 布尔值（true/false，忽略大小写）
     * 2. 数值（BigDecimal）
     * 3. 字符串
     * 
     * @param value 待解析的字符串
     * @return 解析后的对象
     */
    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }

        value = value.trim();

        // 布尔值解析
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }

        // 数值解析
        try {
            return new BigDecimal(value, MATH_CONTEXT);
        } catch (NumberFormatException e) {
            // 解析失败，作为字符串处理
            return value;
        }
    }

    /**
     * 计算表达式 - 委托给ExpressionEvaluator
     * 
     * @param expression 表达式字符串
     * @return 计算结果
     */
    private Object evaluateExpression(String expression) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(this);
        return evaluator.evaluate(expression);
    }

    /**
     * 重新计算依赖单元格 - 性能优化版本
     * 
     * 优化策略：
     * 1. 获取所有受影响的单元格
     * 2. 拓扑排序确定计算顺序
     * 3. 按层级分组并行计算
     * 4. 单个单元格直接计算，多个单元格并行计算
     * 
     * @param cellId 发生变化的单元格ID
     */
    private void recalculateDependents(String cellId) {
        // 1. 获取所有需要重新计算的单元格
        Set<String> allDependents = getAllDependents(cellId);
        if (allDependents.isEmpty()) {
            return;
        }

        // 2. 拓扑排序确定计算顺序，避免依赖冲突
        List<String> sortedCells = topologicalSort(allDependents);

        // 3. 按依赖层级分组，同层级的单元格可以并行计算
        Map<Integer, List<String>> levels = groupByLevel(sortedCells);

        // 4. 按层级顺序计算
        for (List<String> level : levels.values()) {
            if (level.size() == 1) {
                // 单个单元格直接计算，避免线程开销
                Cell cell = cells.get(level.get(0));
                if (cell != null) {
                    calculateCell(cell);
                }
            } else {
                // 多个单元格并行计算
                List<CompletableFuture<Void>> futures = level.stream()
                        .map(id -> CompletableFuture.runAsync(() -> {
                            Cell cell = cells.get(id);
                            if (cell != null) {
                                calculateCell(cell);
                            }
                        }, executor))
                        .toList();

                // 等待当前层级所有单元格计算完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    /**
     * 获取所有依赖于指定单元格的单元格（递归）
     * 
     * 使用深度优先搜索收集所有直接和间接依赖
     * 
     * @param cellId 单元格ID
     * @return 所有依赖单元格的集合
     */
    private Set<String> getAllDependents(String cellId) {
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectDependents(cellId, result, visited);
        return result;
    }

    /**
     * 递归收集依赖单元格
     * 
     * @param cellId 当前单元格ID
     * @param result 结果集合
     * @param visited 已访问集合，防止无限递归
     */
    private void collectDependents(String cellId, Set<String> result, Set<String> visited) {
        if (visited.contains(cellId)) {
            return;
        }
        visited.add(cellId);

        Set<String> directDependents = dependents.get(cellId);
        if (directDependents != null) {
            for (String dependent : directDependents) {
                result.add(dependent);
                collectDependents(dependent, result, visited);
            }
        }
    }

    /**
     * 拓扑排序 - 使用Kahn算法
     * 
     * 确保依赖关系正确的计算顺序：
     * 1. 构建入度表和邻接表
     * 2. 从入度为0的节点开始
     * 3. 逐步移除边并更新入度
     * 4. 输出拓扑序列
     * 
     * @param cellIds 需要排序的单元格ID集合
     * @return 拓扑排序后的单元格ID列表
     */
    private List<String> topologicalSort(Set<String> cellIds) {
        // 初始化入度表和邻接表
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> graph = new HashMap<>();

        // 构建图结构
        for (String cellId : cellIds) {
            inDegree.put(cellId, 0);
            graph.put(cellId, new HashSet<>());
        }

        // 计算入度
        for (String cellId : cellIds) {
            Cell cell = cells.get(cellId);
            if (cell != null) {
                for (String dep : cell.getDependencies()) {
                    if (cellIds.contains(dep)) {
                        graph.get(dep).add(cellId);
                        inDegree.put(cellId, inDegree.get(cellId) + 1);
                    }
                }
            }
        }

        // Kahn算法执行拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);

            // 移除当前节点的所有出边
            for (String neighbor : graph.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        return result;
    }

    /**
     * 按依赖层级分组单元格
     * 
     * 计算每个单元格的依赖层级：
     * - 层级0：不依赖其他单元格
     * - 层级n：依赖的单元格最大层级 + 1
     * 
     * @param sortedCells 拓扑排序后的单元格列表
     * @return 按层级分组的单元格映射
     */
    private Map<Integer, List<String>> groupByLevel(List<String> sortedCells) {
        Map<Integer, List<String>> levels = new HashMap<>();
        Map<String, Integer> cellLevels = new HashMap<>();

        for (String cellId : sortedCells) {
            int level = 0;
            Cell cell = cells.get(cellId);
            if (cell != null) {
                // 计算当前单元格的层级
                for (String dep : cell.getDependencies()) {
                    if (cellLevels.containsKey(dep)) {
                        level = Math.max(level, cellLevels.get(dep) + 1);
                    }
                }
            }

            cellLevels.put(cellId, level);
            levels.computeIfAbsent(level, k -> new ArrayList<>()).add(cellId);
        }

        return levels;
    }

    /**
     * 格式化单元格值为字符串
     * 
     * 格式化规则：
     * 1. BigDecimal：去除尾随零，处理科学计数法
     * 2. Boolean：转换为TRUE/FALSE
     * 3. String：去除多余引号
     * 4. 其他：直接转换为字符串
     * 
     * @param value 单元格值
     * @return 格式化后的字符串
     */
    private String formatCellValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return formatNumber((BigDecimal) value);
        }

        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }

        if (value instanceof String) {
            String str = (String) value;
            // 去除字符串两端的双引号
            if (str.startsWith("\"") && str.endsWith("\"") && str.length() > 1) {
                return str.substring(1, str.length() - 1);
            }
            return str;
        }

        return value.toString();
    }

    /**
     * 格式化数字显示
     * 
     * 优化数字显示格式：
     * 1. 去除无意义的尾随零
     * 2. 大数或小数使用科学计数法
     * 3. 普通数字使用标准格式
     * 
     * @param number 待格式化的数字
     * @return 格式化后的字符串
     */
    private String formatNumber(BigDecimal number) {
        // 去除尾随零
        BigDecimal stripped = number.stripTrailingZeros();

        // 判断是否需要科学计数法
        // 条件：小数位数过多或整数位数过多
        if (stripped.scale() < -6 || stripped.precision() - stripped.scale() > 15) {
            return stripped.toString(); // BigDecimal自动使用科学计数法
        }

        // 使用普通格式
        return stripped.toPlainString();
    }
}
