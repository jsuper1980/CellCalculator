package j2.basic.utils.calc;

import java.lang.reflect.Method;
import java.math.BigDecimal;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个轻量级模拟 Excel 单元格联动的计算引擎，支持单元格引用、公式计算、数学函数和 Java 类调用。
 * 支持的数学函数：
 * - 基础函数：sqrt, abs, ceil, floor, round
 * - 三角函数：sin, cos, tan, asin, acos, atan
 * - 双曲函数：sinh, cosh, tanh
 * - 对数函数：log, log10, exp
 * - 多参数函数：pow, min, max, avg
 * - Java调用：jcall
 * 用法:
 * 1. 初始化引擎：CellCalculator engine = new CellCalculator();
 * 2. 优雅关闭引擎: engine.shutdown();
 * 3. 设置单元格值：engine.set("A1", 10);
 * 4. 设置单元格公式：engine.set("A2", "=A1+1");
 * 5. 获取单元格值：String value = engine.get("A2"); // 结果为 "11"
 * 6. 获取单元格数值: BigDecimal value= engine.getNumber("A2"); // 结果为 11
 * 7. 检查单元格是否存在：boolean exist = engine.exist("A2"); // 结果为 true
 * 8. 获取单元格定义：String definition = engine.getDefinition("A2"); // 结果为 "=A1+1"
 * 9. 删除单元格：engine.del("A2"); // 单元格A2的内容和计算结果都会被清空
 * 
 * @author j² use TRAE
 * @version 1.1
 * @since 20250922
 * @license Apache-2.0 license
 */
public class OldCellCalculator {
  public static final String ERROR = "#ERROR#";

  // 内嵌 Cell 类
  public static class Cell {
    private String id; // 单元格标识，如"A1"
    private String content; // 单元格内容，如"=A1+1"
    private String calculatedValue; // 单元格计算结果，如"11"，若计算错误则为"#ERROR#"
    private Set<String> dependencies; // 单元格依赖的其他单元格，如{"A1"}

    public Cell(String id) {
      this.id = id;
      this.content = "";
      this.calculatedValue = "";
      this.dependencies = new HashSet<>();
    }

    public String getId() {
      return id;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }

    public String getCalculatedValue() {
      return calculatedValue;
    }

    public void setCalculatedValue(String calculatedValue) {
      this.calculatedValue = calculatedValue;
    }

    public Set<String> getDependencies() {
      return new HashSet<>(dependencies);
    }

    public void setDependencies(Set<String> dependencies) {
      this.dependencies = new HashSet<>(dependencies);
    }
  }

  // 存储单元格数据: key为单元格标识如"A1", value为单元格对象
  private final Map<String, Cell> cells = new ConcurrentHashMap<>();
  // 正向依赖表: key为单元格标识, value为依赖该单元格的所有单元格
  private final Map<String, Set<String>> dependents = new ConcurrentHashMap<>();
  // 反向依赖表: key为单元格标识, value为该单元格所依赖的所有单元格
  private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();

  // 读写锁，用于保护计算过程的一致性
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

  // 线程池用于并行计算
  private final ExecutorService executorService = Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors());

  // 关闭方法，用于优雅关闭线程池
  public void shutdown() {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // 单元格引用的正则表达式，用于提取公式中的单元格引用
  // Cell ID只能以字母、下划线、希腊字母、中文开头，不能以数字开头
  // 第一个字符：[\p{L}_φπαβγδεζηθικλμνξοπρστυφχψω] (字母、下划线、希腊字母)
  // 后续字符：[\p{L}\p{N}_φπαβγδεζηθικλμνξοπρστυφχψω]* (字母、数字、下划线、希腊字母)
  private static final Pattern CELL_REFERENCE_PATTERN =
      Pattern.compile("[\\p{L}_φπαβγδεζηθικλμνξοπρστυφχψω][\\p{L}\\p{N}_φπαβγδεζηθικλμνξοπρστυφχψω]*");

  // 内置函数名称集合，Cell ID不能使用这些名称
  private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
      // 基础数学函数
      "sqrt", "abs", "ceil", "floor", "round",
      // 三角函数
      "sin", "cos", "tan", "asin", "acos", "atan",
      // 双曲函数
      "sinh", "cosh", "tanh",
      // 对数函数
      "log", "log10", "exp",
      // 多参数函数
      "pow", "min", "max", "avg",
      // Java类调用函数
      "jcall");

  /**
   * 验证Cell ID是否符合命名规则
   * 
   * @param cellId 单元格ID
   * @throws IllegalArgumentException 如果Cell ID不符合规则
   */
  private void validateCellId(String cellId) {
    if (cellId == null || cellId.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell ID不能为空");
    }

    // 检查是否匹配命名规则
    if (!CELL_REFERENCE_PATTERN.matcher(cellId).matches()) {
      throw new IllegalArgumentException("Cell ID格式不正确: " + cellId +
          "。Cell ID必须以字母、下划线或希腊字母开头，后续可包含字母、数字、下划线或希腊字母");
    }

    // 检查是否与内置函数名冲突
    if (BUILTIN_FUNCTIONS.contains(cellId.toLowerCase())) {
      throw new IllegalArgumentException("Cell ID不能使用内置函数名: " + cellId +
          "。内置函数包括: " + String.join(", ", BUILTIN_FUNCTIONS));
    }
  }

  /**
   * 设置单元格的值或公式
   */
  public void set(String cellId, String content) {
    // 验证Cell ID
    validateCellId(cellId);

    writeLock.lock();
    try {
      // 确保单元格存在
      Cell cell = cells.get(cellId);
      if (cell == null) {
        cell = new Cell(cellId);
        cells.put(cellId, cell);
      }

      // 移除旧的依赖关系
      removeDependencies(cellId);

      // 设置新内容
      cell.setContent(content);

      // 如果是公式，解析并建立新的依赖关系
      if (content.startsWith("=")) {
        Set<String> refs = parseCellReferences(content);
        cell.setDependencies(refs);
        establishDependencies(cellId, refs);
      } else {
        // 普通值，清除依赖
        cell.setDependencies(Collections.emptySet());
      }

      // 触发计算
      calculateDependentCells(cellId);
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * 设置单元格的数值
   * 支持int、long、float、double等数值类型
   */
  public void set(String cellId, Number value) {
    set(cellId, String.valueOf(value));
  }

  /**
   * 设置单元格的整数值
   */
  public void set(String cellId, int value) {
    set(cellId, String.valueOf(value));
  }

  /**
   * 设置单元格的长整数值
   */
  public void set(String cellId, long value) {
    set(cellId, String.valueOf(value));
  }

  /**
   * 设置单元格的浮点数值
   */
  public void set(String cellId, float value) {
    set(cellId, String.valueOf(value));
  }

  /**
   * 设置单元格的双精度浮点数值
   */
  public void set(String cellId, double value) {
    set(cellId, String.valueOf(value));
  }

  /**
   * 删除单元格定义
   * 删除指定的单元格及其所有相关数据，包括内容、计算结果和依赖关系
   * 
   * @param cellId 要删除的单元格ID
   * @throws IllegalArgumentException 如果cellId为null或空字符串
   */
  public void del(String cellId) {
    if (cellId == null || cellId.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell ID不能为空");
    }

    writeLock.lock();
    try {
      // 检查单元格是否存在
      Cell cell = cells.get(cellId);
      if (cell == null) {
        // 单元格不存在，直接返回（幂等操作）
        return;
      }

      // 1. 移除该单元格的所有依赖关系
      removeDependencies(cellId);

      // 2. 从cells映射中移除单元格
      cells.remove(cellId);

      // 3. 清理该单元格作为其他单元格依赖项的记录
      Set<String> cellsThatDependOnThis = dependents.get(cellId);
      if (cellsThatDependOnThis != null) {
        // 遍历所有依赖于被删除单元格的单元格
        for (String dependentCellId : new HashSet<>(cellsThatDependOnThis)) {
          Cell dependentCell = cells.get(dependentCellId);
          if (dependentCell != null) {
            // 从依赖单元格的依赖集合中移除被删除的单元格
            Set<String> deps = dependencies.get(dependentCellId);
            if (deps != null) {
              deps.remove(cellId);
            }

            // 更新依赖单元格的依赖关系
            Set<String> cellDeps = dependentCell.getDependencies();
            cellDeps.remove(cellId);
            dependentCell.setDependencies(cellDeps);
          }
        }
        // 清除正向依赖表中的记录
        dependents.remove(cellId);
      }

      // 4. 重新计算所有依赖于被删除单元格的单元格
      // 这些单元格现在会将被删除的单元格引用视为0或产生错误
      if (cellsThatDependOnThis != null && !cellsThatDependOnThis.isEmpty()) {
        for (String dependentCellId : cellsThatDependOnThis) {
          calculateDependentCells(dependentCellId);
        }
      }

    } finally {
      writeLock.unlock();
    }
  }

  /**
   * 检查单元格是否存在
   * 
   * @param cellId 单元格ID
   * @return 如果单元格存在返回true，否则返回false
   */
  public boolean exist(String cellId) {
    if (cellId == null || cellId.trim().isEmpty()) {
      return false;
    }

    readLock.lock();
    try {
      return cells.containsKey(cellId);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * 格式化BigDecimal，去除无意义的零
   * 例如：1.0000 -> 1, 1.2300 -> 1.23, 0.0 -> 0
   */
  private String formatBigDecimal(BigDecimal value) {
    if (value == null) {
      return null;
    }

    // 使用stripTrailingZeros去除尾随零，然后转换为字符串
    BigDecimal stripped = value.stripTrailingZeros();

    // 如果结果的scale为负数（如1E+2），使用toPlainString()避免科学计数法
    return stripped.scale() < 0 ? stripped.toPlainString() : stripped.toString();
  }

  /**
   * 获取单元格的计算结果, 返回字符串类型
   * 
   * @param cellId 单元格ID
   * @return 单元格的计算结果，若单元格不存在则返回null，计算错误则返回"#ERROR#"
   */
  public String get(String cellId) {
    readLock.lock();
    try {
      Cell cell = cells.get(cellId);
      return cell != null ? cell.getCalculatedValue() : null;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * 获取单元格的数值计算结果
   * 
   * @param cellId 单元格ID
   * @return 单元格的数值计算结果，若单元格不存在或计算错误则返回null
   */
  public BigDecimal getNumber(String cellId) {
    try {
      String value = get(cellId);
      if (value == null || ERROR.equals(value)) {
        return null;
      }
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 获取单元格的定义字符串
   * 
   * @param cellId 单元格ID
   * @return 单元格的原始定义内容，若单元格不存在则返回null
   */
  public String getDefinition(String cellId) {
    readLock.lock();
    try {
      Cell cell = cells.get(cellId);
      return cell != null ? cell.getContent() : null;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * 解析公式中的单元格引用
   */
  private Set<String> parseCellReferences(String formula) {
    Set<String> references = new HashSet<>();
    Matcher matcher = CELL_REFERENCE_PATTERN.matcher(formula);

    while (matcher.find()) {
      String ref = matcher.group(); // 保持原始大小写，支持中文等字符
      references.add(ref);
    }

    return references;
  }

  /**
   * 建立依赖关系（已在写锁保护下调用）
   */
  private void establishDependencies(String cellId, Set<String> dependencies) {
    // 更新反向依赖表
    this.dependencies.put(cellId, ConcurrentHashMap.newKeySet());
    this.dependencies.get(cellId).addAll(dependencies);

    // 更新正向依赖表
    for (String dep : dependencies) {
      dependents.computeIfAbsent(dep, k -> ConcurrentHashMap.newKeySet()).add(cellId);

      // 确保被依赖的单元格存在
      if (!cells.containsKey(dep)) {
        cells.put(dep, new Cell(dep));
      }
    }
  }

  /**
   * 移除单元格的旧依赖关系（已在写锁保护下调用）
   */
  private void removeDependencies(String cellId) {
    Set<String> oldDependencies = dependencies.get(cellId);
    if (oldDependencies != null) {
      // 从正向依赖表中移除
      for (String dep : oldDependencies) {
        Set<String> deps = dependents.get(dep);
        if (deps != null) {
          deps.remove(cellId);
          if (deps.isEmpty()) {
            dependents.remove(dep);
          }
        }
      }
      // 清除反向依赖表
      dependencies.remove(cellId);
    }
  }

  /**
   * 计算所有依赖单元格
   */
  /**
   * 计算依赖单元格（支持并发计算）
   */
  private void calculateDependentCells(String cellId) {
    readLock.lock();
    try {
      // 使用BFS找到所有需要更新的单元格
      Set<String> cellsToUpdate = new HashSet<>();
      Queue<String> queue = new LinkedList<>();

      // 首先计算当前单元格（如果它是公式）
      calculateCell(cellId);

      // 然后找到所有依赖当前单元格的单元格
      queue.add(cellId);

      while (!queue.isEmpty()) {
        String current = queue.poll();
        Set<String> deps = dependents.get(current);
        if (deps != null) {
          for (String dep : deps) {
            if (!cellsToUpdate.contains(dep)) {
              cellsToUpdate.add(dep);
              queue.add(dep);
            }
          }
        }
      }

      // 如果有依赖单元格需要更新
      if (!cellsToUpdate.isEmpty()) {
        // 拓扑排序，确保计算顺序正确
        List<String> sortedCells = topologicalSort(cellsToUpdate);

        // 按层级并行计算
        calculateCellsInParallel(sortedCells);
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * 按层级并行计算单元格
   */
  private void calculateCellsInParallel(List<String> sortedCells) {
    // 构建层级结构
    Map<String, Integer> levels = new HashMap<>();
    Map<Integer, List<String>> levelGroups = new HashMap<>();

    // 计算每个单元格的层级
    for (String cellId : sortedCells) {
      int level = calculateCellLevel(cellId, sortedCells);
      levels.put(cellId, level);
      levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(cellId);
    }

    // 按层级顺序执行，同一层级内串行计算（避免线程池阻塞）
    int maxLevel = levelGroups.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    for (int level = 0; level <= maxLevel; level++) {
      List<String> cellsAtLevel = levelGroups.get(level);
      if (cellsAtLevel != null && !cellsAtLevel.isEmpty()) {
        // 同一层级的单元格串行计算，避免线程池死锁
        for (String cellId : cellsAtLevel) {
          calculateCell(cellId);
        }
      }
    }
  }

  /**
   * 计算单元格在依赖图中的层级
   */
  private int calculateCellLevel(String cellId, List<String> sortedCells) {
    Set<String> deps = dependencies.get(cellId);
    if (deps == null || deps.isEmpty()) {
      return 0;
    }

    int maxDepLevel = -1;
    for (String dep : deps) {
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
   * 拓扑排序，确保计算顺序正确
   */
  private List<String> topologicalSort(Set<String> cellsToUpdate) {
    // 构建入度表
    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, Set<String>> graph = new HashMap<>();

    for (String cell : cellsToUpdate) {
      inDegree.put(cell, 0);
      graph.put(cell, new HashSet<>());
    }

    // 构建临时图
    for (String cell : cellsToUpdate) {
      Set<String> deps = dependencies.get(cell);
      if (deps != null) {
        for (String dep : deps) {
          if (cellsToUpdate.contains(dep)) {
            graph.get(dep).add(cell);
            inDegree.put(cell, inDegree.get(cell) + 1);
          }
        }
      }
    }

    // Kahn算法进行拓扑排序
    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    List<String> result = new ArrayList<>();
    while (!queue.isEmpty()) {
      String node = queue.poll();
      result.add(node);

      for (String neighbor : graph.get(node)) {
        inDegree.put(neighbor, inDegree.get(neighbor) - 1);
        if (inDegree.get(neighbor) == 0) {
          queue.add(neighbor);
        }
      }
    }

    // 检查是否有循环引用
    if (result.size() != cellsToUpdate.size()) {
      throw new RuntimeException("检测到循环引用");
    }

    return result;
  }

  /**
   * 计算单个单元格的值
   */
  private void calculateCell(String cellId) {
    Cell cell = cells.get(cellId);
    if (cell == null)
      return;

    String content = cell.getContent();

    // 如果不是公式，直接将内容作为值
    if (!content.startsWith("=")) {
      synchronized (cell) {
        cell.setCalculatedValue(content);
      }
      return;
    }

    // 处理公式
    try {
      String formula = content.substring(1); // 去除等号
      String evaluatedFormula = replaceCellReferencesWithValues(formula);
      BigDecimal result = evaluateExpression(evaluatedFormula);
      synchronized (cell) {
        cell.setCalculatedValue(formatBigDecimal(result));
      }
    } catch (Exception e) {
      synchronized (cell) {
        cell.setCalculatedValue(ERROR);
      }
    }
  }

  /**
   * 将公式中的单元格引用替换为实际值
   */
  private String replaceCellReferencesWithValues(String formula) {
    // 先处理数学函数，再替换单元格引用
    String processedFormula = processMathFunctions(formula);

    Matcher matcher = CELL_REFERENCE_PATTERN.matcher(processedFormula);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String ref = matcher.group(); // 保持原始大小写，支持中文等字符
      String value = get(ref);

      // 处理未设置值的单元格，视为0
      if (value == null || value.isEmpty() || ERROR.equals(value)) {
        value = "0";
      }

      matcher.appendReplacement(sb, value);
    }

    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * 计算表达式的值（简化版）
   */
  private BigDecimal evaluateExpression(String expression) {
    try {
      return evaluateSimpleExpression(expression.trim());
    } catch (Exception e) {
      throw new RuntimeException("表达式计算错误: " + expression, e);
    }
  }

  /**
   * 简单的数学表达式计算器
   * 支持 +, -, *, /, ^, (, ) 运算符和数学函数
   * 运算符优先级：^ > *, / > +, -
   * 支持函数：sqrt, abs, ceil, floor, round, sin, cos, tan, asin, acos, atan, log, log10, exp
   */
  private BigDecimal evaluateSimpleExpression(String expression) {
    // 处理数学函数
    String processedExpression = processMathFunctions(expression);
    return evaluateSimpleExpressionWithoutFunctions(processedExpression);
  }

  /**
   * 处理数学函数调用
   * 支持基础数学函数、三角函数、对数函数
   */
  private String processMathFunctions(String expression) {
    // 支持嵌套括号的函数模式，包括多参数函数和Java类调用
    Pattern functionPattern =
        Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

    boolean hasFunction = true;
    int maxIterations = 20; // 增加迭代次数以支持更复杂的嵌套
    int iterations = 0;

    while (hasFunction && iterations < maxIterations) {
      hasFunction = false;
      iterations++;

      Matcher matcher = functionPattern.matcher(expression);

      if (matcher.find()) {
        hasFunction = true;
        String functionName = matcher.group(1);
        int functionStart = matcher.start();
        int openParen = matcher.end() - 1; // 开括号位置

        // 验证函数名是否为支持的函数
        if (!BUILTIN_FUNCTIONS.contains(functionName)) {
          throw new RuntimeException("不支持的函数: " + functionName);
        }

        // 找到匹配的闭括号，支持嵌套括号
        int parenCount = 1;
        int closeParen = openParen + 1;

        while (closeParen < expression.length() && parenCount > 0) {
          char c = expression.charAt(closeParen);
          if (c == '(') {
            parenCount++;
          } else if (c == ')') {
            parenCount--;
          }
          closeParen++;
        }

        if (parenCount == 0) {
          // 提取函数参数（括号内的内容）
          String argumentsStr = expression.substring(openParen + 1, closeParen - 1).trim();

          // 递归处理参数中的函数调用
          String processedArgumentsStr = processMathFunctions(argumentsStr);

          // 解析多个参数（用逗号分隔）
          String[] arguments = parseArguments(processedArgumentsStr);

          // 特殊处理jcall函数
          if ("jcall".equals(functionName)) {
            // jcall函数需要特殊处理，前两个参数是字符串
            BigDecimal result = processJavaCall(arguments);
            expression = expression.substring(0, functionStart) + result + expression.substring(closeParen);
          } else {
            // 处理每个参数中的单元格引用
            BigDecimal[] argValues = new BigDecimal[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
              String arg = arguments[i].trim();

              // 替换参数中的单元格引用
              Matcher cellMatcher = CELL_REFERENCE_PATTERN.matcher(arg);
              StringBuffer sb = new StringBuffer();

              while (cellMatcher.find()) {
                String ref = cellMatcher.group();
                String value = get(ref);

                // 处理未设置值的单元格，视为0
                if (value == null || value.isEmpty() || ERROR.equals(value)) {
                  value = "0";
                }

                cellMatcher.appendReplacement(sb, value);
              }

              cellMatcher.appendTail(sb);
              String finalArgument = sb.toString();

              // 计算参数值
              argValues[i] = evaluateSimpleExpressionWithoutFunctions(finalArgument);
            }

            // 计算函数结果
            BigDecimal result = calculateMathFunction(functionName, argValues);

            // 替换函数调用为计算结果
            expression = expression.substring(0, functionStart) + result + expression.substring(closeParen);
          }
        } else {
          // 括号不匹配，跳出循环
          break;
        }
      }
    }

    return expression;
  }

  /**
   * 解析函数参数，支持嵌套括号和逗号分隔
   */
  private String[] parseArguments(String argumentsStr) {
    if (argumentsStr.trim().isEmpty()) {
      return new String[0];
    }

    List<String> arguments = new ArrayList<>();
    int start = 0;
    int parenCount = 0;
    boolean inQuotes = false;
    char quoteChar = 0;

    for (int i = 0; i < argumentsStr.length(); i++) {
      char c = argumentsStr.charAt(i);

      if (!inQuotes && (c == '"' || c == '\'')) {
        inQuotes = true;
        quoteChar = c;
      } else if (inQuotes && c == quoteChar) {
        inQuotes = false;
        quoteChar = 0;
      } else if (!inQuotes) {
        if (c == '(') {
          parenCount++;
        } else if (c == ')') {
          parenCount--;
        } else if (c == ',' && parenCount == 0) {
          // 找到参数分隔符
          arguments.add(argumentsStr.substring(start, i).trim());
          start = i + 1;
        }
      }
    }

    // 添加最后一个参数
    arguments.add(argumentsStr.substring(start).trim());

    return arguments.toArray(new String[0]);
  }

  /**
   * 处理Java类调用函数
   * 
   * @param arguments 参数数组，第一个是类名，第二个是方法名，后续是方法参数
   * @return 调用结果
   */
  private BigDecimal processJavaCall(String[] arguments) {
    if (arguments.length < 2) {
      throw new RuntimeException("jcall函数需要至少2个参数（类名和方法名）");
    }

    try {
      // 获取类名和方法名（去掉引号）
      String className = arguments[0].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
      String methodName = arguments[1].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");

      // 处理方法参数
      Object[] methodArgs = new Object[arguments.length - 2];
      Class<?>[] paramTypes = new Class<?>[arguments.length - 2];

      for (int i = 2; i < arguments.length; i++) {
        String arg = arguments[i].trim();

        // 替换参数中的单元格引用
        Matcher cellMatcher = CELL_REFERENCE_PATTERN.matcher(arg);
        StringBuffer sb = new StringBuffer();

        while (cellMatcher.find()) {
          String ref = cellMatcher.group();
          String value = get(ref);

          // 处理未设置值的单元格，视为0
          if (value == null || value.isEmpty() || ERROR.equals(value)) {
            value = "0";
          }

          cellMatcher.appendReplacement(sb, value);
        }

        cellMatcher.appendTail(sb);
        String finalArgument = sb.toString();

        // 判断参数类型并转换
        if (finalArgument.startsWith("\"") && finalArgument.endsWith("\"") ||
            finalArgument.startsWith("'") && finalArgument.endsWith("'")) {
          // 字符串参数
          methodArgs[i - 2] = finalArgument.substring(1, finalArgument.length() - 1);
          paramTypes[i - 2] = String.class;
        } else {
          // 数值参数
          try {
            BigDecimal value = evaluateSimpleExpressionWithoutFunctions(finalArgument);
            // 尝试转换为整数
            if (value.scale() == 0 && value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0 &&
                value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0) {
              methodArgs[i - 2] = value.intValue();
              paramTypes[i - 2] = int.class;
            } else {
              methodArgs[i - 2] = value.doubleValue();
              paramTypes[i - 2] = double.class;
            }
          } catch (Exception e) {
            // 如果无法解析为数值，当作字符串处理
            methodArgs[i - 2] = finalArgument;
            paramTypes[i - 2] = String.class;
          }
        }
      }

      // 加载类并调用方法
      Class<?> clazz = Class.forName(className);
      Method method = null;

      // 尝试找到匹配的方法
      Method[] methods = clazz.getMethods();
      for (Method m : methods) {
        if (m.getName().equals(methodName) && m.getParameterCount() == paramTypes.length) {
          Class<?>[] methodParamTypes = m.getParameterTypes();
          boolean matches = true;

          for (int i = 0; i < paramTypes.length; i++) {
            if (!isCompatibleType(paramTypes[i], methodParamTypes[i])) {
              matches = false;
              break;
            }
          }

          if (matches) {
            method = m;
            break;
          }
        }
      }

      if (method == null) {
        throw new RuntimeException("找不到匹配的方法: " + className + "." + methodName);
      }

      // 调用方法
      Object result = method.invoke(null, methodArgs);

      // 转换返回值为BigDecimal
      if (result instanceof Number) {
        return BigDecimal.valueOf(((Number) result).doubleValue());
      } else if (result instanceof String) {
        try {
          return new BigDecimal((String) result);
        } catch (NumberFormatException e) {
          // 如果字符串无法转换为数值，返回字符串长度
          return BigDecimal.valueOf(((String) result).length());
        }
      } else if (result instanceof Boolean) {
        return ((Boolean) result) ? BigDecimal.ONE : BigDecimal.ZERO;
      } else {
        // 其他类型返回0
        return BigDecimal.ZERO;
      }

    } catch (Exception e) {
      // 抛出异常，让上层处理并设置为ERROR
      throw new RuntimeException("Java类调用失败: " + e.getMessage(), e);
    }
  }

  /**
   * 检查参数类型是否兼容
   */
  private boolean isCompatibleType(Class<?> provided, Class<?> required) {
    if (provided == required) {
      return true;
    }

    // 数值类型兼容性检查
    if ((provided == int.class || provided == Integer.class) &&
        (required == int.class || required == Integer.class)) {
      return true;
    }

    if ((provided == double.class || provided == Double.class) &&
        (required == double.class || required == Double.class)) {
      return true;
    }

    // int可以转换为double
    if ((provided == int.class || provided == Integer.class) &&
        (required == double.class || required == Double.class)) {
      return true;
    }

    // String类型
    if (provided == String.class && required == String.class) {
      return true;
    }

    return false;
  }

  /**
   * 不处理函数的简单表达式计算器（避免递归）
   */
  private BigDecimal evaluateSimpleExpressionWithoutFunctions(String expression) {
    // 移除空格
    expression = expression.replaceAll("\\s+", "");

    // 处理括号
    while (expression.contains("(")) {
      int start = expression.lastIndexOf('(');
      int end = expression.indexOf(')', start);
      if (end == -1) {
        throw new RuntimeException("括号不匹配");
      }

      String subExpr = expression.substring(start + 1, end);
      BigDecimal subResult = evaluateSimpleExpressionWithoutFunctions(subExpr);
      expression = expression.substring(0, start) + subResult + expression.substring(end + 1);
    }

    // 处理加减法（最低优先级）
    for (int i = expression.length() - 1; i >= 0; i--) {
      char c = expression.charAt(i);
      if ((c == '+' || c == '-') && i > 0) {
        // 确保不是负号
        char prev = expression.charAt(i - 1);
        if (Character.isDigit(prev) || prev == ')') {
          String left = expression.substring(0, i);
          String right = expression.substring(i + 1);
          BigDecimal leftVal = evaluateSimpleExpressionWithoutFunctions(left);
          BigDecimal rightVal = evaluateSimpleExpressionWithoutFunctions(right);
          return c == '+' ? leftVal.add(rightVal) : leftVal.subtract(rightVal);
        }
      }
    }

    // 处理乘除法、整数除、余数除（中等优先级）
    for (int i = expression.length() - 1; i >= 0; i--) {
      char c = expression.charAt(i);
      if ((c == '*' || c == '/' || c == '\\' || c == '%') && i > 0) {
        String left = expression.substring(0, i);
        String right = expression.substring(i + 1);
        BigDecimal leftVal = evaluateSimpleExpressionWithoutFunctions(left);
        BigDecimal rightVal = evaluateSimpleExpressionWithoutFunctions(right);

        // 检查除零错误
        if ((c == '/' || c == '\\' || c == '%') && rightVal.compareTo(BigDecimal.ZERO) == 0) {
          throw new RuntimeException("除零错误");
        }

        switch (c) {
          case '*':
            return leftVal.multiply(rightVal);
          case '/':
            return leftVal.divide(rightVal, 10, RoundingMode.HALF_UP);
          case '\\':
            // 整数除法：向下取整（向负无穷方向舍入）
            return leftVal.divide(rightVal, 0, RoundingMode.FLOOR);
          case '%':
            // 余数运算：实现数学上的模运算（总是非负）
            BigDecimal remainder = leftVal.remainder(rightVal);
            if (remainder.compareTo(BigDecimal.ZERO) < 0) {
              remainder = remainder.add(rightVal.abs());
            }
            return remainder;
          default:
            throw new RuntimeException("未知运算符: " + c);
        }
      }
    }

    // 处理幂运算（最高优先级，右结合）
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '^' && i > 0) {
        String left = expression.substring(0, i);
        String right = expression.substring(i + 1);
        BigDecimal leftVal = evaluateSimpleExpressionWithoutFunctions(left);
        BigDecimal rightVal = evaluateSimpleExpressionWithoutFunctions(right);
        return BigDecimal.valueOf(Math.pow(leftVal.doubleValue(), rightVal.doubleValue()));
      }
    }

    // 处理负号
    if (expression.startsWith("-")) {
      return evaluateSimpleExpressionWithoutFunctions(expression.substring(1)).negate();
    }

    // 解析数字
    try {
      return new BigDecimal(expression);
    } catch (NumberFormatException e) {
      throw new RuntimeException("无法解析数字: " + expression);
    }
  }

  /**
   * 计算具体的数学函数
   */
  private BigDecimal calculateMathFunction(String functionName, BigDecimal[] values) {
    // 对于单参数函数，保持向后兼容
    if (values.length == 1) {
      return calculateMathFunction(functionName, values[0]);
    }

    // 多参数函数
    switch (functionName) {
      case "round":
        if (values.length == 2) {
          // round(value, digits) - 保留指定位数小数
          BigDecimal value = values[0];
          int digits = values[1].intValue();
          return value.setScale(digits, RoundingMode.HALF_UP);
        } else {
          throw new RuntimeException("round函数需要1或2个参数，实际参数个数: " + values.length);
        }

      case "pow":
        if (values.length == 2) {
          // pow(base, exponent) - 幂运算
          return BigDecimal.valueOf(Math.pow(values[0].doubleValue(), values[1].doubleValue()));
        } else {
          throw new RuntimeException("pow函数需要2个参数，实际参数个数: " + values.length);
        }

      case "min":
        if (values.length >= 2) {
          // min(value1, value2, ...) - 最小值
          BigDecimal min = values[0];
          for (int i = 1; i < values.length; i++) {
            min = min.min(values[i]);
          }
          return min;
        } else {
          throw new RuntimeException("min函数需要至少2个参数，实际参数个数: " + values.length);
        }

      case "max":
        if (values.length >= 2) {
          // max(value1, value2, ...) - 最大值
          BigDecimal max = values[0];
          for (int i = 1; i < values.length; i++) {
            max = max.max(values[i]);
          }
          return max;
        } else {
          throw new RuntimeException("max函数需要至少2个参数，实际参数个数: " + values.length);
        }

      case "avg":
        if (values.length >= 1) {
          // avg(value1, value2, ...) - 平均值
          BigDecimal sum = BigDecimal.ZERO;
          for (BigDecimal value : values) {
            sum = sum.add(value);
          }
          return sum.divide(BigDecimal.valueOf(values.length), 10, RoundingMode.HALF_UP);
        } else {
          throw new RuntimeException("avg函数需要至少1个参数，实际参数个数: " + values.length);
        }

      case "jcall":
        if (values.length >= 2) {
          // jcall(className, methodName, param1, param2, ...) - Java类调用
          // 注意：前两个参数是字符串，需要特殊处理
          throw new RuntimeException("jcall函数需要特殊处理，不能通过数值参数调用");
        } else {
          throw new RuntimeException("jcall函数需要至少2个参数（类名和方法名），实际参数个数: " + values.length);
        }

      default:
        // 对于其他函数，如果是单参数，调用原方法
        if (values.length == 1) {
          return calculateMathFunction(functionName, values[0]);
        } else {
          throw new RuntimeException("函数 " + functionName + " 不支持多参数调用");
        }
    }
  }

  private BigDecimal calculateMathFunction(String functionName, BigDecimal value) {
    switch (functionName) {
      // 基础数学函数
      case "sqrt":
        if (value.compareTo(BigDecimal.ZERO) < 0) {
          throw new RuntimeException("sqrt函数参数不能为负数: " + value);
        }
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
      case "abs":
        return value.abs();
      case "ceil":
        return BigDecimal.valueOf(Math.ceil(value.doubleValue()));
      case "floor":
        return BigDecimal.valueOf(Math.floor(value.doubleValue()));
      case "round": {
        return BigDecimal.valueOf(Math.round(value.doubleValue()));
      }

      // 三角函数
      case "sin":
        return BigDecimal.valueOf(Math.sin(value.doubleValue()));
      case "cos":
        return BigDecimal.valueOf(Math.cos(value.doubleValue()));
      case "tan":
        return BigDecimal.valueOf(Math.tan(value.doubleValue()));
      case "asin":
        if (value.compareTo(BigDecimal.valueOf(-1)) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
          throw new RuntimeException("asin函数参数必须在[-1,1]范围内: " + value);
        }
        return BigDecimal.valueOf(Math.asin(value.doubleValue()));
      case "acos":
        if (value.compareTo(BigDecimal.valueOf(-1)) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
          throw new RuntimeException("acos函数参数必须在[-1,1]范围内: " + value);
        }
        return BigDecimal.valueOf(Math.acos(value.doubleValue()));
      case "atan":
        return BigDecimal.valueOf(Math.atan(value.doubleValue()));

      // 双曲函数
      case "sinh":
        return BigDecimal.valueOf(Math.sinh(value.doubleValue()));
      case "cosh":
        return BigDecimal.valueOf(Math.cosh(value.doubleValue()));
      case "tanh":
        return BigDecimal.valueOf(Math.tanh(value.doubleValue()));

      // 对数函数
      case "log":
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
          throw new RuntimeException("log函数参数必须大于0: " + value);
        }
        return BigDecimal.valueOf(Math.log(value.doubleValue()));

      case "log10":
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
          throw new RuntimeException("log10函数参数必须大于0: " + value);
        }
        return BigDecimal.valueOf(Math.log10(value.doubleValue()));
      case "exp":
        return BigDecimal.valueOf(Math.exp(value.doubleValue()));

      // 多参数函数的单参数版本
      case "avg":
        return value; // 单个数的平均值就是它本身

      default:
        throw new RuntimeException("不支持的函数: " + functionName);
    }
  }
}
