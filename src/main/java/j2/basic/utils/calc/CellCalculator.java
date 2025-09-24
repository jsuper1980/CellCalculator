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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单元格计算引擎
 * 支持单元格引用、公式计算、数学函数、逻辑运算和Java类调用
 * 具备依赖管理、循环检测、高精度计算、并发安全等特性
 */
public class CellCalculator {

    // 单元格存储
    private final ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();

    // 依赖关系图：cellId -> 依赖它的单元格集合
    private final ConcurrentHashMap<String, Set<String>> dependents = new ConcurrentHashMap<>();

    // 读写锁保证并发安全
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // 线程池用于并行计算
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    // 数学计算上下文，保证高精度
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    // 内置函数名集合
    private static final Set<String> BUILT_IN_FUNCTIONS = Set.of(
            "sqrt", "abs", "ceil", "floor", "round", "sin", "cos", "tan",
            "asin", "acos", "atan", "sinh", "cosh", "tanh", "log", "log10",
            "exp", "pow", "min", "max", "avg", "and", "or", "not", "xor",
            "if", "ifs", "jcall");

    // 单元格ID验证正则表达式
    private static final Pattern CELL_ID_PATTERN = Pattern.compile(
            "^[\\p{L}_][\\p{L}\\p{N}_]*$");

    // 单元格引用提取正则表达式
    private static final Pattern CELL_REFERENCE_PATTERN = Pattern.compile(
            "\\b([\\p{L}_][\\p{L}\\p{N}_]*)\\b");

    /**
     * 设置单元格值
     * 
     * @param cellId 单元格标识
     * @param content 单元格内容
     */
    public void set(String cellId, String content) {
        validateCellId(cellId);

        lock.writeLock().lock();
        try {
            Cell cell = cells.computeIfAbsent(cellId, Cell::new);
            cell.setDefine(content);
            cell.setError(null);

            // 解析依赖关系
            Set<String> oldDependencies = cell.getDependencies();
            Set<String> newDependencies = extractDependencies(content);

            // 检查循环引用
            checkCircularReference(cellId, newDependencies);

            // 更新依赖关系
            updateDependencies(cellId, oldDependencies, newDependencies);
            cell.setDependencies(newDependencies);

            // 计算单元格值
            calculateCell(cell);

            // 重新计算依赖此单元格的其他单元格
            recalculateDependents(cellId);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 设置单元格数值
     */
    public void set(String cellId, Number value) {
        set(cellId, value.toString());
    }

    public void set(String cellId, int value) {
        set(cellId, String.valueOf(value));
    }

    public void set(String cellId, long value) {
        set(cellId, String.valueOf(value));
    }

    public void set(String cellId, float value) {
        set(cellId, String.valueOf(value));
    }

    public void set(String cellId, double value) {
        set(cellId, String.valueOf(value));
    }

    public void set(String cellId, boolean value) {
        set(cellId, String.valueOf(value));
    }

    /**
     * 获取单元格字符串值
     * 
     * @param cellId 单元格标识
     * @return 单元格值的字符串表示
     */
    public String get(String cellId) {
        lock.readLock().lock();
        try {
            Cell cell = cells.get(cellId);
            if (cell == null) {
                return null;
            }

            // 如果有错误，value应该为null，直接返回null
            if (cell.hasError()) {
                return null;
            }

            Object value = cell.getValue();
            if (value == null) {
                return null;
            }

            if (value instanceof BigDecimal) {
                return formatNumber((BigDecimal) value);
            }

            if (value instanceof Boolean) {
                return ((Boolean) value) ? "TRUE" : "FALSE";
            }

            // 处理字符串值，去除多余的引号
            if (value instanceof String) {
                String str = (String) value;
                // 如果字符串被双引号包围，去除引号
                if (str.startsWith("\"") && str.endsWith("\"") && str.length() > 1) {
                    return str.substring(1, str.length() - 1);
                }
                return str;
            }

            return value.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取单元格数值
     * 
     * @param cellId 单元格标识
     * @return 单元格的BigDecimal值
     */
    public BigDecimal getNumber(String cellId) {
        lock.readLock().lock();
        try {
            Cell cell = cells.get(cellId);
            if (cell == null || cell.hasError()) {
                return null;
            }

            Object value = cell.getValue();
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取单元格定义
     * 
     * @param cellId 单元格标识
     * @return 单元格的原始定义字符串
     */
    public String getDefine(String cellId) {
        lock.readLock().lock();
        try {
            Cell cell = cells.get(cellId);
            return cell != null ? cell.getDefine() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取单元格错误信息
     * 
     * @param cellId 单元格标识
     * @return 错误信息
     */
    public String getError(String cellId) {
        lock.readLock().lock();
        try {
            Cell cell = cells.get(cellId);
            return cell != null ? cell.getError() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取单元格类型
     * 
     * @param cellId 单元格标识
     * @return 单元格值的类型
     */
    public String getType(String cellId) {
        lock.readLock().lock();
        try {
            Cell cell = cells.get(cellId);
            return cell != null ? cell.getValueType() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 删除单元格
     * 
     * @param cellId 单元格标识
     */
    public void del(String cellId) {
        lock.writeLock().lock();
        try {
            Cell cell = cells.remove(cellId);
            if (cell != null) {
                // 清理依赖关系
                updateDependencies(cellId, cell.getDependencies(), Collections.emptySet());
                dependents.remove(cellId);

                // 重新计算依赖此单元格的其他单元格
                recalculateDependents(cellId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检查单元格是否存在
     * 
     * @param cellId 单元格标识
     * @return 是否存在
     */
    public boolean exist(String cellId) {
        lock.readLock().lock();
        try {
            return cells.containsKey(cellId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 关闭计算引擎，释放线程池资源
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 验证单元格ID的有效性
     */
    private void validateCellId(String cellId) {
        if (cellId == null || cellId.trim().isEmpty()) {
            throw new IllegalArgumentException("单元格ID不能为空");
        }

        if (!CELL_ID_PATTERN.matcher(cellId).matches()) {
            throw new IllegalArgumentException("无效的单元格ID: " + cellId);
        }

        if (BUILT_IN_FUNCTIONS.contains(cellId.toLowerCase())) {
            throw new IllegalArgumentException("单元格ID不能使用内置函数名: " + cellId);
        }
    }

    /**
     * 提取单元格内容中的依赖关系
     */
    private Set<String> extractDependencies(String content) {
        Set<String> dependencies = new HashSet<>();

        if (content == null || !content.startsWith("=")) {
            return dependencies;
        }

        String formula = content.substring(1);
        Matcher matcher = CELL_REFERENCE_PATTERN.matcher(formula);

        while (matcher.find()) {
            String reference = matcher.group(1);
            if (!BUILT_IN_FUNCTIONS.contains(reference.toLowerCase()) &&
                    CELL_ID_PATTERN.matcher(reference).matches()) {
                dependencies.add(reference);
            }
        }

        return dependencies;
    }

    /**
     * 检查循环引用
     */
    private void checkCircularReference(String cellId, Set<String> newDependencies) {
        Set<String> visited = new HashSet<>();
        if (hasCircularReference(cellId, newDependencies, visited)) {
            throw new RuntimeException("检测到循环引用: " + cellId);
        }
    }

    private boolean hasCircularReference(String cellId, Set<String> dependencies, Set<String> visited) {
        if (visited.contains(cellId)) {
            return true;
        }

        visited.add(cellId);

        for (String dep : dependencies) {
            Cell depCell = cells.get(dep);
            if (depCell != null) {
                if (hasCircularReference(dep, depCell.getDependencies(), visited)) {
                    return true;
                }
            }
        }

        visited.remove(cellId);
        return false;
    }

    /**
     * 更新依赖关系图
     */
    private void updateDependencies(String cellId, Set<String> oldDeps, Set<String> newDeps) {
        // 移除旧的依赖关系
        if (oldDeps != null) {
            for (String oldDep : oldDeps) {
                dependents.computeIfPresent(oldDep, (k, v) -> {
                    v.remove(cellId);
                    return v.isEmpty() ? null : v;
                });
            }
        }

        // 添加新的依赖关系
        if (newDeps != null) {
            for (String newDep : newDeps) {
                dependents.computeIfAbsent(newDep, k -> ConcurrentHashMap.newKeySet()).add(cellId);
            }
        }
    }

    /**
     * 计算单元格值
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
                // 字符串字面量
                cell.setValue(define.substring(1, define.length() - 1));
            } else {
                // 尝试解析为数值或布尔值
                Object value = parseValue(define);
                cell.setValue(value);
            }
        } catch (Exception e) {
            cell.setError(e.getMessage());
            cell.setValue(null);
        }
    }

    /**
     * 解析值
     */
    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }

        value = value.trim();

        // 布尔值
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }

        // 数值
        try {
            return new BigDecimal(value, MATH_CONTEXT);
        } catch (NumberFormatException e) {
            // 字符串
            return value;
        }
    }

    /**
     * 计算表达式
     */
    private Object evaluateExpression(String expression) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(this);
        return evaluator.evaluate(expression);
    }

    /**
     * 重新计算依赖单元格
     */
    private void recalculateDependents(String cellId) {
        Set<String> allDependents = getAllDependents(cellId);
        if (allDependents.isEmpty()) {
            return;
        }

        // 拓扑排序确定计算顺序
        List<String> sortedCells = topologicalSort(allDependents);

        // 并行计算同层级的单元格
        Map<Integer, List<String>> levels = groupByLevel(sortedCells);

        for (List<String> level : levels.values()) {
            if (level.size() == 1) {
                // 单个单元格直接计算
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

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    /**
     * 获取所有依赖于指定单元格的单元格（递归）
     */
    private Set<String> getAllDependents(String cellId) {
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectDependents(cellId, result, visited);
        return result;
    }

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
     * 拓扑排序
     */
    private List<String> topologicalSort(Set<String> cellIds) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> graph = new HashMap<>();

        // 构建图和入度表
        for (String cellId : cellIds) {
            inDegree.put(cellId, 0);
            graph.put(cellId, new HashSet<>());
        }

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

        // Kahn算法
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
     * 按层级分组
     */
    private Map<Integer, List<String>> groupByLevel(List<String> sortedCells) {
        Map<Integer, List<String>> levels = new HashMap<>();
        Map<String, Integer> cellLevels = new HashMap<>();

        for (String cellId : sortedCells) {
            int level = 0;
            Cell cell = cells.get(cellId);
            if (cell != null) {
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
     * 格式化数字，去除无意义的尾随零，并处理科学计数法
     */
    private String formatNumber(BigDecimal number) {
        // 去除尾随零
        BigDecimal stripped = number.stripTrailingZeros();

        // 如果数字太大或太小，使用科学计数法
        if (stripped.scale() < -6 || stripped.precision() - stripped.scale() > 15) {
            return stripped.toString(); // 自动使用科学计数法
        }

        // 否则使用普通格式
        return stripped.toPlainString();
    }

    /**
     * 获取单元格值用于计算
     */
    Object getCellValue(String cellId) {
        Cell cell = cells.get(cellId);
        if (cell == null) {
            throw new RuntimeException("单元格不存在: " + cellId);
        }

        if (cell.hasError()) {
            throw new RuntimeException("单元格计算错误: " + cellId + " - " + cell.getError());
        }

        return cell.getValue();
    }
}
