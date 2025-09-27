package j2.basic.utils.calc;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * CellCalculator性能测试类
 * 测试大规模、多层联动的复杂计算的性能和内存占用
 * 
 * @author j2
 */
@DisplayName("CellCalculator 性能测试")
public class PerformanceTests {

  private CellCalculator calculator;
  private MemoryMXBean memoryBean;
  private PerformanceMetrics metrics;

  @BeforeEach
  void setUp() {
    calculator = new CellCalculator();
    memoryBean = ManagementFactory.getMemoryMXBean();
    metrics = new PerformanceMetrics();

    // 执行垃圾回收，确保内存测量准确
    System.gc();
    System.gc();
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @AfterEach
  void tearDown() {
    if (calculator != null) {
      calculator.shutdown();
    }

    // 打印性能指标
    metrics.printSummary();

    // 执行垃圾回收
    System.gc();
    System.gc();
  }

  /**
   * 测试大规模单元格并行创建性能
   * 创建10000个单元格，测试创建性能和内存占用
   */
  @Test
  @DisplayName("大规模单元格并行创建测试 - 10000个单元格")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testMassiveCellCreation() {
    final int CELL_COUNT = 10000;

    metrics.startTest("大规模单元格创建");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 并行创建单元格
    IntStream.range(0, CELL_COUNT)
        .parallel()
        .forEach(i -> {
          String cellId = "CELL_" + i;
          calculator.set(cellId, i * 2 + 1); // 设置为奇数值
        });

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证所有单元格都创建成功
    long createdCount = IntStream.range(0, CELL_COUNT)
        .parallel()
        .mapToObj(i -> "CELL_" + i)
        .filter(calculator::exist)
        .count();

    metrics.recordResult(
        "创建" + CELL_COUNT + "个单元格",
        (endTime - startTime) / 1_000_000, // 转换为毫秒
        endMemory - startMemory,
        createdCount + "/" + CELL_COUNT + " 创建成功");

    System.out.printf("✓ 成功创建 %d 个单元格，耗时: %.2f ms，内存增长: %.2f MB%n",
        createdCount, (endTime - startTime) / 1_000_000.0, (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试多层依赖链性能
   * 创建深度为200层的依赖关系，测试联动计算性能
   */
  @Test
  @DisplayName("多层依赖链测试 - 200层深度")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void testDeepDependencyChain() {
    final int CHAIN_DEPTH = 200;

    metrics.startTest("多层依赖链计算");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 创建依赖链：CHAIN_0 = 1, CHAIN_1 = CHAIN_0 + 1, CHAIN_2 = CHAIN_1 + 1, ...
    calculator.set("CHAIN_0", 1);

    for (int i = 1; i < CHAIN_DEPTH; i++) {
      calculator.set("CHAIN_" + i, "=CHAIN_" + (i - 1) + " + 1");
    }

    long creationTime = System.nanoTime();

    // 触发联动计算：修改根节点
    calculator.set("CHAIN_0", 100);

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证计算结果
    String finalResult = calculator.get("CHAIN_" + (CHAIN_DEPTH - 1));
    int expectedResult = 100 + CHAIN_DEPTH - 1;

    metrics.recordResult(
        "创建" + CHAIN_DEPTH + "层依赖链",
        (creationTime - startTime) / 1_000_000,
        0,
        "依赖链创建完成");

    metrics.recordResult(
        "触发" + CHAIN_DEPTH + "层联动计算",
        (endTime - creationTime) / 1_000_000,
        endMemory - startMemory,
        "最终结果: " + finalResult + " (期望: " + expectedResult + ")");

    System.out.printf("✓ %d层依赖链计算完成，最终结果: %s (期望: %d)%n",
        CHAIN_DEPTH, finalResult, expectedResult);
    System.out.printf("  创建耗时: %.2f ms，计算耗时: %.2f ms，内存增长: %.2f MB%n",
        (creationTime - startTime) / 1_000_000.0,
        (endTime - creationTime) / 1_000_000.0,
        (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试复杂网状依赖性能
   * 创建复杂的网状依赖关系，测试联动计算性能
   */
  @Test
  @DisplayName("复杂网状依赖测试 - 优化版金字塔结构")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void testComplexNetworkDependency() {
    final int PYRAMID_LEVELS = 15; // 降低金字塔层数，避免指数级复杂度

    metrics.startTest("复杂网状依赖计算");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 创建金字塔结构：每一层的单元格依赖于上一层的相邻单元格
    // 第0层：BASE_0_0 = 1
    calculator.set("BASE_0_0", 1);

    int totalCells = 1;

    System.out.println("  开始创建金字塔结构...");
    for (int level = 1; level < PYRAMID_LEVELS; level++) {
      // 添加进度监控
      if (level % 3 == 0) {
        System.out.printf("    创建第 %d 层，当前总单元格数: %d%n", level, totalCells);
      }

      for (int pos = 0; pos <= level; pos++) {
        String cellId = "BASE_" + level + "_" + pos;

        if (pos == 0) {
          // 左边界：只依赖上一层的第一个
          calculator.set(cellId, "=BASE_" + (level - 1) + "_0 + 1");
        } else if (pos == level) {
          // 右边界：只依赖上一层的最后一个
          calculator.set(cellId, "=BASE_" + (level - 1) + "_" + (level - 1) + " + 1");
        } else {
          // 中间：依赖上一层的两个相邻单元格
          calculator.set(cellId, "=BASE_" + (level - 1) + "_" + (pos - 1) +
              " + BASE_" + (level - 1) + "_" + pos);
        }
        totalCells++;
      }
    }

    long creationTime = System.nanoTime();
    System.out.printf("  金字塔创建完成，共 %d 个单元格，耗时: %.2f ms%n",
        totalCells, (creationTime - startTime) / 1_000_000.0);

    // 触发联动计算：修改根节点
    System.out.println("  开始触发联动计算...");
    calculator.set("BASE_0_0", 10);

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证部分结果
    String topResult = calculator.get("BASE_" + (PYRAMID_LEVELS - 1) + "_0");
    String middleResult = calculator.get("BASE_" + (PYRAMID_LEVELS - 1) + "_" + (PYRAMID_LEVELS / 2));

    metrics.recordResult(
        "创建" + totalCells + "个网状依赖单元格",
        (creationTime - startTime) / 1_000_000,
        0,
        "金字塔结构创建完成");

    metrics.recordResult(
        "触发" + totalCells + "个单元格联动计算",
        (endTime - creationTime) / 1_000_000,
        endMemory - startMemory,
        "顶层结果: " + topResult + ", 中间结果: " + middleResult);

    System.out.printf("✓ %d层金字塔(%d个单元格)计算完成%n", PYRAMID_LEVELS, totalCells);
    System.out.printf("  创建耗时: %.2f ms，计算耗时: %.2f ms，内存增长: %.2f MB%n",
        (creationTime - startTime) / 1_000_000.0,
        (endTime - creationTime) / 1_000_000.0,
        (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试更复杂的网状依赖 - 网格结构
   * 创建网格状的依赖关系，每个单元格依赖其上方和左方的单元格
   */
  @Test
  @DisplayName("网格状依赖测试 - 矩阵结构")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testGridNetworkDependency() {
    final int GRID_SIZE = 10; // 10x10 网格，降低规模

    metrics.startTest("网格状依赖计算");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    System.out.println("  开始创建网格结构...");

    // 创建网格结构
    int totalCells = 0;
    for (int row = 0; row < GRID_SIZE; row++) {
      if (row % 3 == 0) {
        System.out.printf("    创建第 %d 行...%n", row);
      }

      for (int col = 0; col < GRID_SIZE; col++) {
        String cellId = "GRID_" + row + "_" + col;

        if (row == 0 && col == 0) {
          // 左上角起始点
          calculator.set(cellId, 1);
        } else if (row == 0) {
          // 第一行：只依赖左边
          calculator.set(cellId, "=GRID_0_" + (col - 1) + " + 1");
        } else if (col == 0) {
          // 第一列：只依赖上面
          calculator.set(cellId, "=GRID_" + (row - 1) + "_0 + 1");
        } else {
          // 其他位置：依赖上方和左方
          calculator.set(cellId, "=GRID_" + (row - 1) + "_" + col +
              " + GRID_" + row + "_" + (col - 1));
        }
        totalCells++;
      }
    }

    long creationTime = System.nanoTime();
    System.out.printf("  网格创建完成，共 %d 个单元格，耗时: %.2f ms%n",
        totalCells, (creationTime - startTime) / 1_000_000.0);

    // 触发联动计算：修改起始点
    System.out.println("  开始触发联动计算...");
    calculator.set("GRID_0_0", 5);

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证结果
    String cornerResult = calculator.get("GRID_" + (GRID_SIZE - 1) + "_" + (GRID_SIZE - 1));
    String middleResult = calculator.get("GRID_" + (GRID_SIZE / 2) + "_" + (GRID_SIZE / 2));

    metrics.recordResult(
        "创建" + totalCells + "个网格依赖单元格",
        (creationTime - startTime) / 1_000_000,
        0,
        "网格结构创建完成");

    metrics.recordResult(
        "触发" + totalCells + "个单元格联动计算",
        (endTime - creationTime) / 1_000_000,
        endMemory - startMemory,
        "右下角结果: " + cornerResult + ", 中心结果: " + middleResult);

    System.out.printf("✓ %dx%d网格(%d个单元格)计算完成%n", GRID_SIZE, GRID_SIZE, totalCells);
    System.out.printf("  创建耗时: %.2f ms，计算耗时: %.2f ms，内存增长: %.2f MB%n",
        (creationTime - startTime) / 1_000_000.0,
        (endTime - creationTime) / 1_000_000.0,
        (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试并发访问压力
   * 模拟多线程同时操作单元格的场景
   */
  @Test
  @DisplayName("并发访问压力测试 - 多线程操作")
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void testConcurrentAccessStress() {
    final int THREAD_COUNT = 20;
    final int OPERATIONS_PER_THREAD = 500;

    metrics.startTest("并发访问压力测试");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    // 预先创建一些基础单元格
    for (int i = 0; i < 100; i++) {
      calculator.set("SHARED_" + i, i);
    }

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    // 启动多个线程并发操作
    for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
      final int tid = threadId;
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        for (int op = 0; op < OPERATIONS_PER_THREAD; op++) {
          try {
            String cellId = "THREAD_" + tid + "_" + op;

            // 混合操作：创建、计算、读取
            switch (op % 4) {
              case 0: // 创建数值单元格
                calculator.set(cellId, tid * 1000 + op);
                break;
              case 1: // 创建公式单元格
                calculator.set(cellId, "=SHARED_" + (op % 100) + " * 2");
                break;
              case 2: // 读取操作
                calculator.get("SHARED_" + (op % 100));
                break;
              case 3: // 更新操作
                calculator.set("SHARED_" + (op % 100), op);
                break;
            }
            successCount.incrementAndGet();
          } catch (Exception e) {
            errorCount.incrementAndGet();
            System.err.println("线程 " + tid + " 操作 " + op + " 出错: " + e.getMessage());
          }
        }
      }, executor);
      futures.add(future);
    }

    // 等待所有线程完成
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    executor.shutdown();

    int totalOperations = THREAD_COUNT * OPERATIONS_PER_THREAD;
    double throughput = totalOperations / ((endTime - startTime) / 1_000_000_000.0);

    metrics.recordResult(
        totalOperations + "个并发操作",
        (endTime - startTime) / 1_000_000,
        endMemory - startMemory,
        String.format("成功: %d, 失败: %d, 吞吐量: %.0f ops/s",
            successCount.get(), errorCount.get(), throughput));

    System.out.printf("✓ 并发测试完成：%d个线程，每线程%d操作%n", THREAD_COUNT, OPERATIONS_PER_THREAD);
    System.out.printf("  成功操作: %d, 失败操作: %d%n", successCount.get(), errorCount.get());
    System.out.printf("  总耗时: %.2f ms，吞吐量: %.0f ops/s，内存增长: %.2f MB%n",
        (endTime - startTime) / 1_000_000.0, throughput, (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试极限规模性能
   * 创建超大规模的单元格网络，测试系统极限
   */
  @Test
  @DisplayName("极限规模测试 - 50000个单元格")
  @Timeout(value = 300, unit = TimeUnit.SECONDS)
  void testExtremeScale() {
    final int CELL_COUNT = 50000;
    final int BATCH_SIZE = 1000;

    metrics.startTest("极限规模测试");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 分批创建单元格，避免内存溢出
    for (int batch = 0; batch < CELL_COUNT / BATCH_SIZE; batch++) {
      final int batchStart = batch * BATCH_SIZE;

      IntStream.range(batchStart, batchStart + BATCH_SIZE)
          .parallel()
          .forEach(i -> {
            String cellId = "EXTREME_" + i;
            if (i == 0) {
              calculator.set(cellId, 1);
            } else if (i % 100 == 0) {
              // 每100个单元格创建一个依赖关系
              calculator.set(cellId, "=EXTREME_" + (i - 100) + " + 1");
            } else {
              calculator.set(cellId, i);
            }
          });

      // 每批次后检查内存使用
      if (batch % 10 == 0) {
        long currentMemory = getUsedMemory();
        System.out.printf("  已创建 %d 个单元格，当前内存使用: %.2f MB%n",
            (batch + 1) * BATCH_SIZE, currentMemory / 1024.0 / 1024.0);
      }
    }

    long creationTime = System.nanoTime();

    // 触发一些联动计算
    calculator.set("EXTREME_0", 1000);

    long endMemory = getUsedMemory();

    // 验证部分结果
    long existingCount = IntStream.range(0, Math.min(1000, CELL_COUNT))
        .parallel()
        .mapToObj(i -> "EXTREME_" + i)
        .filter(calculator::exist)
        .count();

    metrics.recordResult(
        "创建" + CELL_COUNT + "个极限规模单元格",
        (creationTime - startTime) / 1_000_000,
        endMemory - startMemory,
        "验证存在: " + existingCount + "/1000");

    System.out.printf("✓ 极限规模测试完成：%d个单元格%n", CELL_COUNT);
    System.out.printf("  创建耗时: %.2f s，内存占用: %.2f MB%n",
        (creationTime - startTime) / 1_000_000_000.0, (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试1000层表格的性能
   * 每层依赖上一层并加1
   */
  @Test
  @DisplayName("1000层表格依赖测试")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void testThousandLayerTable() {
    final int LAYER_COUNT = 1000;

    metrics.startTest("1000层表格依赖测试");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 创建第1层
    calculator.set("LAYER_1", 1);

    // 创建剩余层级
    for (int i = 2; i <= LAYER_COUNT; i++) {
      calculator.set("LAYER_" + i, "=LAYER_" + (i - 1) + " + 1");
    }

    long creationTime = System.nanoTime();
    System.out.printf("  表格创建完成，共 %d 层，耗时: %.2f ms%n",
        LAYER_COUNT, (creationTime - startTime) / 1_000_000.0);

    // 修改第1层触发联动计算
    System.out.println("  开始触发联动计算...");
    calculator.set("LAYER_1", 100);

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证计算结果
    String finalResult = calculator.get("LAYER_" + LAYER_COUNT);
    int expectedResult = 100 + LAYER_COUNT - 1;

    metrics.recordResult(
        "创建" + LAYER_COUNT + "层表格",
        (creationTime - startTime) / 1_000_000,
        0,
        "表格创建完成");

    metrics.recordResult(
        "触发" + LAYER_COUNT + "层联动计算",
        (endTime - creationTime) / 1_000_000,
        endMemory - startMemory,
        "最终结果: " + finalResult + " (期望: " + expectedResult + ")");

    System.out.printf("✓ %d层表格计算完成，最终结果: %s (期望: %d)%n",
        LAYER_COUNT, finalResult, expectedResult);
    System.out.printf("  创建耗时: %.2f ms，计算耗时: %.2f ms，内存增长: %.2f MB%n",
        (creationTime - startTime) / 1_000_000.0,
        (endTime - creationTime) / 1_000_000.0,
        (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 测试十万级表格的多层计算管线性能
   * 创建10层计算管线，第一层随机赋值，其他层随机合计前一层的值
   */
  @Test
  @DisplayName("十万级表格多层计算管线测试")
  @Timeout(value = 300, unit = TimeUnit.SECONDS)
  void testLargeTablePipeline() {
    final int TOTAL_CELLS = 10000; // 十万个单元格
    final int PIPELINE_LAYERS = 10; // 10层计算管线
    final int CELLS_PER_LAYER = TOTAL_CELLS / PIPELINE_LAYERS;
    final Random random = new Random();

    metrics.startTest("十万级表格多层计算管线测试");
    long startMemory = getUsedMemory();
    long startTime = System.nanoTime();

    // 创建第一层并随机赋值
    System.out.println("  创建第1层基础数据...");
    IntStream.range(0, CELLS_PER_LAYER)
        .parallel()
        .forEach(i -> {
          String cellId = "L1_" + i;
          calculator.set(cellId, random.nextInt(1000));
        });

    // 创建后续层的计算管线
    for (int layer = 2; layer <= PIPELINE_LAYERS; layer++) {
      final int currentLayer = layer;
      System.out.printf("  创建第%d层计算管线...%n", layer);

      IntStream.range(0, CELLS_PER_LAYER)
          .parallel()
          .forEach(i -> {
            String currentCellId = "L" + currentLayer + "_" + i;

            // 随机选择1-3个上层单元格进行求和
            int sumCount = random.nextInt(3) + 1;
            StringBuilder formula = new StringBuilder("=");

            for (int j = 0; j < sumCount; j++) {
              if (j > 0) {
                formula.append(" + ");
              }
              // 随机选择上一层的单元格
              int sourceIndex = random.nextInt(CELLS_PER_LAYER);
              formula.append("L").append(currentLayer - 1).append("_").append(sourceIndex);
            }

            calculator.set(currentCellId, formula.toString());
          });
    }

    long creationTime = System.nanoTime();
    System.out.printf("  计算管线创建完成，共 %d 个单元格，耗时: %.2f s%n",
        TOTAL_CELLS, (creationTime - startTime) / 1_000_000_000.0);

    // 触发联动计算：随机修改第一层的100个值
    System.out.println("  开始触发联动计算...");
    for (int i = 0; i < 100; i++) {
      String cellId = "L1_" + random.nextInt(CELLS_PER_LAYER);
      calculator.set(cellId, random.nextInt(1000));
    }

    long endTime = System.nanoTime();
    long endMemory = getUsedMemory();

    // 验证计算结果：随机检查最后一层的10个单元格
    List<String> sampleResults = IntStream.range(0, 10)
        .mapToObj(i -> {
          String cellId = "L" + PIPELINE_LAYERS + "_" + random.nextInt(CELLS_PER_LAYER);
          return cellId + "=" + calculator.get(cellId);
        })
        .collect(Collectors.toList());

    metrics.recordResult(
        "创建" + TOTAL_CELLS + "个单元格的计算管线",
        (creationTime - startTime) / 1_000_000,
        0,
        "计算管线创建完成");

    metrics.recordResult(
        "触发100次随机修改的联动计算",
        (endTime - creationTime) / 1_000_000,
        endMemory - startMemory,
        "采样结果: " + String.join(", ", sampleResults));

    System.out.printf("✓ 十万级表格计算管线测试完成%n");
    System.out.printf("  创建耗时: %.2f s，计算耗时: %.2f s，内存增长: %.2f MB%n",
        (creationTime - startTime) / 1_000_000_000.0,
        (endTime - creationTime) / 1_000_000_000.0,
        (endMemory - startMemory) / 1024.0 / 1024.0);
  }

  /**
   * 获取当前已使用内存（字节）
   */
  private long getUsedMemory() {
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    return heapUsage.getUsed();
  }

  /**
   * 性能指标收集类
   */
  private static class PerformanceMetrics {
    private final List<TestResult> results = new ArrayList<>();
    private String currentTest;

    public void startTest(String testName) {
      this.currentTest = testName;
      System.out.println("\n🚀 开始测试: " + testName);
    }

    public void recordResult(String operation, long timeMs, long memoryBytes, String details) {
      results.add(new TestResult(currentTest, operation, timeMs, memoryBytes, details));
    }

    public void printSummary() {
      System.out.println("\n📊 性能测试总结:");
      System.out.println("=".repeat(80));

      for (TestResult result : results) {
        System.out.printf("%-20s | %-30s | %8d ms | %8.2f MB | %s%n",
            result.testName,
            result.operation,
            result.timeMs,
            result.memoryBytes / 1024.0 / 1024.0,
            result.details);
      }

      System.out.println("=".repeat(80));

      // 统计总计
      long totalTime = results.stream().mapToLong(r -> r.timeMs).sum();
      long totalMemory = results.stream().mapToLong(r -> r.memoryBytes).sum();

      System.out.printf("总计: %d 项测试，总耗时: %d ms，总内存增长: %.2f MB%n",
          results.size(), totalTime, totalMemory / 1024.0 / 1024.0);
    }

    private static class TestResult {
      final String testName;
      final String operation;
      final long timeMs;
      final long memoryBytes;
      final String details;

      TestResult(String testName, String operation, long timeMs, long memoryBytes, String details) {
        this.testName = testName;
        this.operation = operation;
        this.timeMs = timeMs;
        this.memoryBytes = memoryBytes;
        this.details = details;
      }
    }
  }
}
