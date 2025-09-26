package j2.basic.utils.calc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

/**
 * 单元格计算引擎
 * 
 * @author j2
 * @version 1.2.0
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
  private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

  // ==================== 公共API方法 ====================

  /**
   * 设置单元格内容 - 核心方法
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
   * 获取单元格字符串值
   * 
   * @param cellId 单元格标识
   * @return 单元格值的字符串表示，错误或不存在时返回null
   */
  public String get(String cellId) {
    Cell cell = getCellSafely(cellId);

    // 处理单元格不存在或有错误的情况
    if (cell == null || cell.hasError()) {
      return null;
    }

    // 格式化并返回值
    return CalculatorUtils.formatCellValue(cell.getValue());
  }

  /**
   * 获取单元格数值
   * 
   * @param cellId 单元格标识
   * @return 单元格的BigDecimal值，非数值或错误时返回null
   */
  public BigDecimal getNumber(String cellId) {
    Cell cell = getCellSafely(cellId);

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
   * @return 单元格的原始定义字符串，不存在时返回null
   */
  public String getDefine(String cellId) {
    Cell cell = getCellSafely(cellId);
    return cell != null ? cell.getDefine() : null;
  }

  /**
   * 获取单元格错误信息
   * 
   * @param cellId 单元格标识
   * @return 错误信息，无错误时返回null
   */
  public String getError(String cellId) {
    Cell cell = getCellSafely(cellId);
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
   * 获取单元格原始值
   * 
   * @param cellId 单元格标识
   * @return 单元格的原始值，不存在时返回null
   */
  public Object getRawValue(String cellId) {
    Cell cell = cells.get(cellId);
    return cell != null ? cell.getValue() : null;
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


  /**
   * 将内存中的表格定义保存到输出流
   * 格式：CellID:CellDefine，每行一个单元格
   * 保存过程中不触发联动计算
   * 
   * @param outputStream 输出流
   * @throws IOException 当写入失败时
   */
  public void save(OutputStream outputStream) throws IOException {
    long stamp = lock.readLock();
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
      // 遍历所有单元格，保存非空定义的单元格
      for (Map.Entry<String, Cell> entry : cells.entrySet()) {
        String cellId = entry.getKey();
        Cell cell = entry.getValue();
        String define = cell.getDefine();

        // 只保存有定义内容的单元格
        if (define != null && !define.isEmpty()) {
          writer.write(cellId + ":" + define);
          writer.newLine();
        }
      }
      writer.flush();
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * 从输入流载入表格定义到内存
   * 格式：CellID:CellDefine，每行一个单元格
   * 载入过程中不触发联动计算，只设置单元格定义
   * 
   * @param inputStream 输入流
   * @throws IOException 当读取失败时
   * @throws IllegalArgumentException 当数据格式错误时
   */
  public void load(InputStream inputStream) throws IOException {
    long stamp = lock.writeLock();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;

      while ((line = reader.readLine()) != null) {
        lineNumber++;
        line = line.trim();

        // 跳过空行
        if (line.isEmpty()) {
          continue;
        }

        // 解析格式：CellID:CellDefine
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
          throw new IllegalArgumentException("第" + lineNumber + "行格式错误，缺少冒号分隔符: " + line);
        }

        String cellId = line.substring(0, colonIndex).trim();
        String cellDefine = line.substring(colonIndex + 1);

        // 验证单元格ID
        validateCellId(cellId);

        // 创建或获取单元格，只设置定义，不计算值
        Cell cell = cells.computeIfAbsent(cellId, Cell::new);
        cell.setDefine(cellDefine);
        cell.setValue(null); // 清空值，等待重算
        cell.setError(null); // 清空错误信息

        // 解析依赖关系但不更新依赖图（避免触发计算）
        Set<String> dependencies = extractDependencies(cellDefine);
        cell.setDependencies(dependencies);
      }
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  /**
   * 重新计算整个表格
   * 从最底层（无依赖的单元格）开始，按依赖关系顺序重新计算所有单元格
   * 这个方法通常在load之后调用，以确保所有单元格值都是最新的
   */
  public void recalculate() {
    long stamp = lock.writeLock();
    try {
      // 1. 重建依赖关系图
      dependents.clear();
      for (Map.Entry<String, Cell> entry : cells.entrySet()) {
        String cellId = entry.getKey();
        Cell cell = entry.getValue();
        Set<String> dependencies = cell.getDependencies();

        if (dependencies != null) {
          for (String dependency : dependencies) {
            dependents.computeIfAbsent(dependency, k -> ConcurrentHashMap.newKeySet()).add(cellId);
          }
        }
      }

      // 2. 获取所有需要计算的单元格
      Set<String> allCellIds = new HashSet<>(cells.keySet());

      // 3. 拓扑排序确定计算顺序
      List<String> sortedCells = topologicalSort(allCellIds);

      // 4. 按顺序重新计算所有单元格
      calculateCellsInParallel(sortedCells);

    } finally {
      lock.unlockWrite(stamp);
    }
  }

  /**
   * 清空所有单元格数据
   * 
   * 执行步骤:
   * 1. 获取写锁
   * 2. 清空cells和dependents
   */
  public void clear() {
    long stamp = lock.writeLock();
    try {
      cells.clear();
      dependents.clear();
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 线程安全地获取单元格对象
   * 
   * 使用乐观读锁提高并发性能：
   * 1. 先尝试乐观读
   * 2. 如果数据在读取过程中被修改，则升级为悲观读锁
   * 
   * @param cellId 单元格标识
   * @return 单元格对象，不存在时返回null
   */
  private Cell getCellSafely(String cellId) {
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

    return cell;
  }

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

    if (!CalculatorUtils.CELL_ID_PATTERN.matcher(cellId).matches()) {
      throw new IllegalArgumentException("无效的单元格ID: " + cellId +
          " (必须以字母或下划线开头，后跟字母、数字或下划线)");
    }

    if (CalculatorUtils.BUILT_IN_FUNCTIONS.contains(cellId.toLowerCase())) {
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
    Matcher matcher = CalculatorUtils.CELL_REFERENCE_PATTERN.matcher(formula);

    // 查找所有匹配的标识符
    while (matcher.find()) {
      String reference = matcher.group(1);

      // 排除内置函数名和无效的单元格ID
      if (!CalculatorUtils.BUILT_IN_FUNCTIONS.contains(reference.toLowerCase())
          && CalculatorUtils.CELL_ID_PATTERN.matcher(reference).matches()) {
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
      throw new RuntimeException("检测到循环引用 " + cellId + " -> " + String.join(" -> ", visited));
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
        Object value = CalculatorUtils.parseValue(define);
        cell.setValue(value);
      }
    } catch (Exception e) {
      // 计算出错时记录错误信息，值设为null
      cell.setError(e.getMessage());
      cell.setValue(null);
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
   * 重新计算依赖单元格
   * 
   * 当单元格值发生变化时，需要重新计算所有依赖它的单元格
   * 使用拓扑排序确保计算顺序正确，并按层级并行计算提高性能
   * 
   * @param cellId 发生变化的单元格ID
   */
  private void recalculateDependents(String cellId) {
    long startTime = CalculatorUtils.DEBUG_MODE ? System.nanoTime() : 0;

    // 1. 获取所有需要重新计算的单元格
    Set<String> allDependents = getAllDependents(cellId);
    if (allDependents.isEmpty()) {
      return;
    }

    CalculatorUtils.debugPrint("[DEBUG] 重新计算依赖: %s -> %d个依赖单元格%n", cellId, allDependents.size());

    // 2. 拓扑排序确定计算顺序
    List<String> sortedCells = topologicalSort(allDependents);

    // 3. 按层级并行计算（参考 OldCellCalculator 的设计）
    calculateCellsInParallel(sortedCells);

    if (CalculatorUtils.DEBUG_MODE) {
      long duration = System.nanoTime() - startTime;
      CalculatorUtils.debugPrint("[DEBUG] 依赖计算完成: 耗时 %.2f ms%n", duration / 1_000_000.0);
    }
  }

  /**
   * 按层级并行计算单元格
   * 参考 OldCellCalculator 的设计，避免线程池死锁问题
   * 
   * 优化策略：
   * 1. 同一层级内的单元格可以并行计算（无依赖关系）
   * 2. 不同层级间必须串行执行（有依赖关系）
   * 3. 避免过度使用线程池，防止死锁
   * 
   * @param sortedCells 拓扑排序后的单元格列表
   */
  private void calculateCellsInParallel(List<String> sortedCells) {
    // 构建层级结构
    Map<String, Integer> cellLevels = new HashMap<>();
    Map<Integer, List<String>> levelGroups = new HashMap<>();

    // 计算每个单元格的层级（基于依赖深度）
    for (String cellId : sortedCells) {
      int level = calculateCellLevel(cellId, sortedCells);
      cellLevels.put(cellId, level);
      levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(cellId);
    }

    // 按层级顺序执行
    int maxLevel = levelGroups.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    CalculatorUtils.debugPrint("[DEBUG] 层级计算: 共%d层，%d个单元格%n", maxLevel + 1, sortedCells.size());

    for (int level = 0; level <= maxLevel; level++) {
      List<String> cellsAtLevel = levelGroups.get(level);
      if (cellsAtLevel != null && !cellsAtLevel.isEmpty()) {

        long levelStartTime = CalculatorUtils.DEBUG_MODE ? System.nanoTime() : 0;

        if (cellsAtLevel.size() == 1) {
          // 单个单元格直接计算，避免线程池开销
          String cellId = cellsAtLevel.get(0);
          Cell cell = cells.get(cellId);
          if (cell != null) {
            calculateCell(cell);
          }
          CalculatorUtils.debugPrint("[DEBUG] 第%d层: 1个单元格(串行)%n", level);
        } else if (cellsAtLevel.size() <= 4) {
          // 少量单元格串行计算，避免线程池开销
          for (String cellId : cellsAtLevel) {
            Cell cell = cells.get(cellId);
            if (cell != null) {
              calculateCell(cell);
            }
          }
          CalculatorUtils.debugPrint("[DEBUG] 第%d层: %d个单元格(串行)%n", level, cellsAtLevel.size());
        } else {
          // 大量单元格并行计算，提高性能
          List<CompletableFuture<Void>> futures = cellsAtLevel.stream()
              .map(cellId -> CompletableFuture.runAsync(() -> {
                Cell cell = cells.get(cellId);
                if (cell != null) {
                  calculateCell(cell);
                }
              }, executor))
              .toList();

          // 等待当前层级所有计算完成
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
          CalculatorUtils.debugPrint("[DEBUG] 第%d层: %d个单元格(并行)%n", level, cellsAtLevel.size());
        }

        if (CalculatorUtils.DEBUG_MODE) {
          long levelDuration = System.nanoTime() - levelStartTime;
          CalculatorUtils.debugPrint("[DEBUG] 第%d层计算完成: 耗时 %.2f ms%n", level, levelDuration / 1_000_000.0);
        }
      }
    }
  }

  /**
   * 计算单元格在依赖图中的层级
   * 参考 OldCellCalculator 的 calculateCellLevel 方法
   * 
   * @param cellId 单元格ID
   * @param sortedCells 拓扑排序后的单元格列表
   * @return 单元格的层级（0为最底层）
   */
  private int calculateCellLevel(String cellId, List<String> sortedCells) {
    Cell cell = cells.get(cellId);
    if (cell == null || cell.getDependencies() == null || cell.getDependencies().isEmpty()) {
      return 0;
    }

    int maxDepLevel = -1;
    for (String dep : cell.getDependencies()) {
      if (sortedCells.contains(dep)) {
        int depIndex = sortedCells.indexOf(dep);
        if (depIndex > maxDepLevel) {
          maxDepLevel = depIndex;
        }
      }
    }

    return maxDepLevel + 1;
  }

  /**
   * 获取所有依赖单元格（递归）
   * 
   * @param cellId 单元格ID
   * @return 所有依赖的单元格ID集合
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
   * @param visited 已访问集合（防止循环依赖）
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
   * 拓扑排序 - 确定计算顺序
   * 
   * 使用Kahn算法进行拓扑排序，确保依赖关系正确
   * 
   * @param cellIds 需要排序的单元格ID集合
   * @return 排序后的单元格ID列表
   */
  private List<String> topologicalSort(Set<String> cellIds) {
    // 构建子图的入度表
    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, Set<String>> subGraph = new HashMap<>();

    // 初始化
    for (String cellId : cellIds) {
      inDegree.put(cellId, 0);
      subGraph.put(cellId, new HashSet<>());
    }

    // 计算子图中的依赖关系和入度
    for (String cellId : cellIds) {
      Cell cell = cells.get(cellId);
      if (cell != null && cell.getDependencies() != null) {
        for (String dependency : cell.getDependencies()) {
          if (cellIds.contains(dependency)) {
            subGraph.get(dependency).add(cellId);
            inDegree.put(cellId, inDegree.get(cellId) + 1);
          }
        }
      }
    }

    // Kahn算法
    Queue<String> queue = new LinkedList<>();
    List<String> result = new ArrayList<>();

    // 找到所有入度为0的节点
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.offer(entry.getKey());
      }
    }

    // 处理队列
    while (!queue.isEmpty()) {
      String current = queue.poll();
      result.add(current);

      // 更新邻接节点的入度
      for (String neighbor : subGraph.get(current)) {
        int newInDegree = inDegree.get(neighbor) - 1;
        inDegree.put(neighbor, newInDegree);
        if (newInDegree == 0) {
          queue.offer(neighbor);
        }
      }
    }

    return result;
  }
}
