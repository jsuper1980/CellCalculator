# 单元格计算引擎 (CellCalculator)

一个高性能的内存表格计算引擎，支持类似 Excel 的公式计算、单元格依赖关系管理和可视化导出。

**作者**: j² use TRAE
**版本**: 1.2.0
**日期**: 2025-09-26
**Java 版本**: 17+  
**许可证**: Apache License 2.0

## 🚀 特性

### 核心功能

- **依赖管理**: 自动处理单元格间的依赖关系，支持联动计算
- **公式计算**: 支持复杂的数学表达式和单元格引用
- **逻辑运算**: 支持比较运算符、逻辑运算符和逻辑函数
- **单元格管理**: 支持灵活的单元格命名（支持中文、希腊字母、下划线等）
- **并发安全**: 使用读写锁保证多线程环境下的数据一致性
- **循环检测**: 自动检测并防止循环引用
- **高精度计算**: 使用 BigDecimal 进行数值计算，避免浮点数精度问题
- **智能格式化**: 自动去除计算结果中无意义的尾随零（如 1.0000 显示为 1）
- **完整运算符支持**: 支持基础四则运算、整数除法、余数运算、幂运算、逻辑运算等
- **数据持久化**: 支持保存和加载计算数据
- **可视化导出**: 支持将单元格依赖关系导出为 SVG 图形
- **性能优化**: 支持并行计算和拓扑排序优化

### 数学运算符

- **基础运算**: `+`, `-`, `*`, `/`
- **整数除法**: `\` (向负无穷方向舍入的整数除法)
- **余数运算**: `%` (数学模运算，结果总是非负数)
- **幂运算**: `^` (支持任意实数幂)
- **括号**: `()` (支持嵌套)

### 逻辑运算符

- **比较运算符**: `==`, `!=`, `<`, `<=`, `>`, `>=`
- **逻辑运算符**: `&&` (逻辑与), `||` (逻辑或)

### 内置数学函数

#### 基础数学函数

- `sqrt(x)` - 平方根
- `abs(x)` - 绝对值
- `ceil(x)` - 向上取整
- `floor(x)` - 向下取整
- `round(x)` - 四舍五入
- `round(x, digits)` - 保留指定小数位数

#### 三角函数

- `sin(x)`, `cos(x)`, `tan(x)` - 基础三角函数
- `asin(x)`, `acos(x)`, `atan(x)` - 反三角函数

#### 双曲函数

- `sinh(x)`, `cosh(x)`, `tanh(x)` - 双曲函数

#### 对数函数

- `log(x)` - 自然对数
- `log10(x)` - 常用对数
- `exp(x)` - 指数函数

#### 多参数函数

- `pow(base, exponent)` - 幂运算
- `min(x1, x2, ...)` - 最小值
- `max(x1, x2, ...)` - 最大值
- `avg(x1, x2, ...)` - 平均值

#### 逻辑函数

- `and(x1, x2, ...)` - 逻辑与，所有参数都为真（非零）则返回 1，否则返回 0
- `or(x1, x2, ...)` - 逻辑或，任一参数为真（非零）则返回 1，否则返回 0
- `not(x)` - 逻辑非，参数为 0 则返回 1，否则返回 0
- `xor(x1, x2, ...)` - 逻辑异或，奇数个参数为真则返回 1，否则返回 0
- `if(condition, trueValue, falseValue)` - 条件函数，条件为真返回 trueValue，否则返回 falseValue
- `ifs(condition1, value1, condition2, value2, ..., [elseValue])` - 多条件函数，按顺序检查条件，返回第一个满足条件的值，可选默认值

#### Java 类调用

- `jcall(className, methodName, param1, param2, ...)` - 调用 Java 静态方法，支持任意参数类型，包括基本类型、包装类、字符串、布尔值等。

## 📖 使用指南

### 基本用法

## 📖 使用指南

### 基本用法

```java
// 1. 创建引擎实例
CellCalculator calculator = new CellCalculator();

// 2. 设置单元格值
calculator.set("A1", 10);
calculator.set("A2", 20);

// 3. 设置公式
calculator.set("A3", "=A1+A2");

// 4. 获取计算结果
String result = calculator.get("A3"); // "30"
BigDecimal numResult = calculator.getNumber("A3"); // 30
Object rawValue = calculator.getRawValue("A3"); // BigDecimal(30)

// 5. 获取单元格定义
String definition = calculator.getDefine("A3"); // "=A1+A2"

// 6. 获取单元格类型
String type = calculator.getType("A3"); // "number"

// 7. 高精度计算示例
calculator.set("B1", "=0.1+0.2");
System.out.println(calculator.get("B1")); // "0.3"

calculator.set("B2", "=10/2");
System.out.println(calculator.get("B2")); // "5"

// 8. 数据持久化
try (FileOutputStream fos = new FileOutputStream("data.txt")) {
    calculator.save(fos);
}

try (FileInputStream fis = new FileInputStream("data.txt")) {
    calculator.load(fis);
}

// 9. 可视化导出
SvgExporter exporter = new SvgExporter(calculator);
try (FileOutputStream fos = new FileOutputStream("dependencies.svg")) {
    exporter.export(fos);
}

// 10. 关闭引擎（释放线程池资源）
calculator.shutdown();
```

### 单元格命名规则

支持灵活的单元格命名：

```java
// ✅ 支持的命名方式
calculator.set("A1", 100);           // 传统Excel风格
calculator.set("π", Math.PI);        // 希腊字母
calculator.set("半径", 10);           // 中文
calculator.set("_temp", 42);         // 下划线开头
calculator.set("value_α", 100);      // 混合命名

// ❌ 不支持的命名方式
// calculator.set("123abc", 100);    // 不能以数字开头
// calculator.set("sqrt", 100);      // 不能使用内置函数名
```

### 数学表达式示例

```java
// 基础运算
calculator.set("B1", "=10+20*3");        // 70
calculator.set("B2", "=(10+20)*3");      // 90
calculator.set("B3", "=2^3");            // 8 (幂运算)

// 整数除法和余数运算
calculator.set("B4", "=17\\5");          // 3 (整数除法)
calculator.set("B5", "=17%5");           // 2 (余数运算)
calculator.set("B6", "=-17\\5");         // -4 (负数整数除法)
calculator.set("B7", "=-17%5");          // 3 (负数余数运算，结果总是非负)

// 数学函数
calculator.set("C1", "=sqrt(25)");       // 5.0
calculator.set("C2", "=sin(π/2)");       // 1.0
calculator.set("C3", "=log(exp(2))");    // 2.0

// 多参数函数
calculator.set("D1", "=max(10,20,30)");  // 30
calculator.set("D2", "=avg(10,20,30)");  // 20.0
calculator.set("D3", "=pow(2,10)");      // 1024.0

// 逻辑运算符
calculator.set("E1", "=10>5");           // true
calculator.set("E2", "=10==10");         // true
calculator.set("E3", "=10!=5");          // true
calculator.set("E4", "=10>0 && 10<20");  // true
calculator.set("E5", "=10<0 || 10>5");   // true

// 逻辑函数
calculator.set("F1", "=and(10>0, 10<20)");       // true
calculator.set("F2", "=or(10<0, 10>5)");         // true
calculator.set("F3", "=not(0)");                 // true
calculator.set("F3", "=not(1)");                 // false
calculator.set("F3", "=not(true)");              // false
calculator.set("F4", "=xor(1, 0, 1)");           // false (偶数个真值)
calculator.set("F4", "=xor(true, false, false)");// true (奇数个真值)
calculator.set("F5", "=if(10>5, 100, 200)");     // 100
calculator.set("F5", "=if(true, '真', '假')");    // 真
calculator.set("F6", "=ifs(B2>=90, 'A', B2>=80, 'B', B2>=70, 'C', B2>=60, 'D')"); // A (成绩等级)
```

### 复杂公式示例

```java
// 圆的面积和周长计算
calculator.set("π", Math.PI);
calculator.set("半径", 10);
calculator.set("面积", "=π*半径^2");      // 314.159...
calculator.set("周长", "=2*π*半径");      // 62.831...

// 嵌套函数
calculator.set("G1", "=sqrt(abs(-36))"); // 6.0
calculator.set("G2", "=round(π*半径^2, 2)"); // 314.16

// 复合逻辑表达式
calculator.set("G3", "=if(and(半径>0, 半径<20), π*半径^2, 0)"); // 314.159...
calculator.set("G4", "=if(or(半径<=0, 半径>=100), 0, 2*π*半径)"); // 62.831...
```

### Java 类调用示例

```java
// 调用Math类的静态方法
calculator.set("F1", "=jcall('java.lang.Math', 'random')");  // 随机数
calculator.set("F2", "=jcall('java.lang.Math', 'max', 10, 20)");  // 20

// 调用String类的静态方法
calculator.set("F3", "=jcall('java.lang.String', 'valueOf', 123)");  // "123"
calculator.set("F4", "=jcall('java.lang.String', 'valueOf', true)");  // "true"
```

### 获取单元格定义

除了获取计算结果，您还可以获取单元格的原始定义字符串：

```java
// 设置单元格
calculator.set("A1", 10);
calculator.set("A2", "=A1*2+5");
calculator.set("A3", "Hello world"); // 等价 calculator.set("A3", "'Hello world'");

// 获取计算结果
String result = calculator.get("A2");        // "25"
BigDecimal number = calculator.getNumber("A2"); // 25

// 获取原始定义
String definition = calculator.getDefine("A2"); // "=A1*2+5"

// 获取单元格类型
String type = calculator.getType("A2");      // "number"

// 对于数值单元格
String numDef = calculator.getDefine("A1");     // "10"
```

这个功能特别适用于：

- **公式调试**: 查看单元格的原始公式
- **数据导出**: 保存单元格的定义而非计算结果
- **公式编辑**: 获取现有公式进行修改
- **审计追踪**: 记录单元格的定义历史

### 依赖关系和联动计算

```java
// 设置依赖链
calculator.set("X1", 10);
calculator.set("X2", "=X1*2");      // X2 = 20
calculator.set("X3", "=X2+X1");     // X3 = 30

// 更新X1会自动触发X2和X3的重新计算
calculator.set("X1", 20);
// 现在: X2 = 40, X3 = 60
```

## 🔧 高级特性

### 数据持久化

引擎支持将计算数据保存到文件并重新加载：

```java
CellCalculator calculator = new CellCalculator();

// 设置一些数据
calculator.set("A1", 100);
calculator.set("A2", "=A1*2");
calculator.set("名称", "'产品A'");

// 保存到文件
try (FileOutputStream fos = new FileOutputStream("calculator_data.txt")) {
    calculator.save(fos);
    System.out.println("数据已保存");
}

// 清空当前数据
calculator.clear();

// 从文件加载
try (FileInputStream fis = new FileInputStream("calculator_data.txt")) {
    calculator.load(fis);
    System.out.println("数据已加载");
    System.out.println("A2 = " + calculator.get("A2")); // "200"
    System.out.println("名称 = " + calculator.get("名称")); // "产品A"
}
```

### 可视化导出

使用 `SvgExporter` 类可以将单元格依赖关系导出为美观的 SVG 图形：

```java
CellCalculator calculator = new CellCalculator();

// 创建一些有依赖关系的单元格
calculator.set("收入", 1000000);
calculator.set("成本", 600000);
calculator.set("毛利", "=收入-成本");
calculator.set("税率", 0.25);
calculator.set("税后利润", "=毛利*(1-税率)");

// 导出依赖关系图
SvgExporter exporter = new SvgExporter(calculator);
try (FileOutputStream fos = new FileOutputStream("financial_dependencies.svg")) {
    exporter.export(fos);
    System.out.println("依赖关系图已导出为 SVG 格式");
}
```

导出的 SVG 图形具有以下特点：

- **智能布局**: 自动使用 Sugiyama 算法进行层次化布局
- **美观样式**: 不同类型的单元格使用不同颜色
- **交互效果**: 支持鼠标悬停高亮效果
- **依赖箭头**: 清晰显示单元格间的依赖关系

### 多线程安全

引擎使用 `StampedLock` 机制，提供更好的并发性能，支持多线程并发访问：

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

for (int i = 0; i < 4; i++) {
    final int threadId = i;
    executor.submit(() -> {
        calculator.set("Thread" + threadId, threadId * 100);
        calculator.set("Result" + threadId, "=Thread" + threadId + "*2");
    });
}
```

### 🔧 高级特性

### 高精度计算

引擎使用 **BigDecimal** 进行所有数值计算，彻底解决浮点数精度问题：

```java
// 传统 double 计算的问题
double result1 = 0.1 + 0.2;  // 0.30000000000000004
double result2 = 1.0 - 0.9;  // 0.09999999999999998

// CellCalculator 的高精度计算
calculator.set("A1", "=0.1+0.2");  // "0.3"
calculator.set("A2", "=1.0-0.9");  // "0.1"
calculator.set("A3", "=0.1*3");    // "0.3"
```

### 智能格式化

自动优化显示格式，去除无意义的尾随零：

```java
calculator.set("B1", "=10/2");     // "5"
calculator.set("B2", "=1.0000");   // "1"
calculator.set("B3", "=10/3");     // "3.3333333333" (保留必要的小数位)
```

### 错误处理

- 支持除零检测、参数范围检查等
- 循环引用会抛出运行时异常

### 性能优化

- **拓扑排序**: 确保依赖单元格按正确顺序计算
- **并行计算**: 同层级的独立单元格可并行计算
- **线程池**: 使用固定大小的线程池处理计算任务

## 📋 API 参考

### 主要方法

#### 设置单元格值

```java
void set(String cellId, String definition)
void set(String cellId, Number definition)
void set(String cellId, int definition)
void set(String cellId, long definition)
void set(String cellId, float definition)
void set(String cellId, double definition)
void set(String cellId, boolean definition)
```

#### 获取单元格值

```java
String get(String cellId)           // 获取字符串结果
BigDecimal getNumber(String cellId) // 获取数值结果（BigDecimal类型）
Object getRawValue(String cellId)   // 获取原始值对象
String getDefine(String cellId)     // 获取单元格的原始定义字符串
String getError(String cellId)      // 获取单元格的错误信息
String getType(String cellId)       // 获取单元格的类型（number、string、boolean）
```

#### 单元格管理

```java
void del(String cellId)      // 删除单元格
boolean exist(String cellId) // 检查单元格是否存在
void clear()                 // 清空所有单元格
void recalculate()          // 重新计算所有单元格
```

#### 数据持久化

```java
void save(OutputStream outputStream) throws IOException  // 保存数据到输出流
void load(InputStream inputStream) throws IOException    // 从输入流加载数据
```

#### 可视化导出

```java
// 使用SvgExporter类导出依赖关系图
SvgExporter exporter = new SvgExporter(calculator);
exporter.export(outputStream);  // 导出为SVG格式
```

#### 资源管理

```java
void shutdown()  // 关闭线程池，释放资源
```

### Cell 类

内嵌的单元格类，包含以下属性：

- `id`: 单元格标识
- `content`: 单元格内容（原始公式或值）
- `calculatedValue`: 计算结果
- `dependencies`: 依赖的其他单元格集合

## 🛠️ 技术实现

### 核心组件

1. **单元格存储**: 使用 `ConcurrentHashMap` 存储单元格数据
2. **依赖管理**: 维护正向和反向依赖关系图
3. **表达式解析**: 支持复杂数学表达式的递归解析
4. **函数处理**: 模块化的函数计算系统
5. **并发控制**: 使用 `StampedLock` 提供更好的并发性能
6. **高精度计算**: 基于 BigDecimal 的数值计算引擎
7. **智能格式化**: 自动优化数值显示格式
8. **线程池管理**: 根据 CPU 核心数动态调整线程池大小
9. **数据持久化**: 支持文本格式的数据保存和加载
10. **可视化导出**: 基于 Sugiyama 算法的 SVG 图形导出

### 计算流程

1. **公式解析**: 提取单元格引用和函数调用
2. **依赖建立**: 更新依赖关系图
3. **循环检测**: 检查并防止循环引用
4. **拓扑排序**: 确定计算顺序
5. **并行计算**: 按层级并行执行计算
6. **结果更新**: 更新单元格计算结果

### 项目结构

```
src/main/java/j2/basic/utils/calc/
├── CellCalculator.java      # 主计算引擎类
├── Cell.java               # 单元格数据模型
├── ExpressionEvaluator.java # 表达式求值器
├── CalculatorUtils.java    # 工具类
├── SvgExporter.java        # SVG导出器
└── TokenIterator.java      # 词法分析器
```

## 📝 注意事项

1. **资源管理**: 使用完毕后请调用 `shutdown()` 方法释放线程池资源
2. **单元格命名**: 避免使用内置函数名作为单元格 ID
3. **循环引用**: 系统会自动检测并抛出异常
4. **数值精度**: 内部使用 **BigDecimal** 进行高精度计算，完全避免浮点数精度问题
5. **Java 调用**: `jcall` 函数只能调用公共静态方法
6. **格式化**: 计算结果会自动去除无意义的尾随零，提供更友好的显示格式
7. **数据持久化**: 保存的数据格式为文本格式，包含单元格 ID 和定义
8. **SVG 导出**: 导出的 SVG 文件可在浏览器中查看，支持交互效果
9. **并发安全**: 所有公共方法都是线程安全的，支持多线程并发访问
10. **内存管理**: 大量单元格时注意内存使用，可使用 `clear()` 方法清理数据

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>j2.basic</groupId>
    <artifactId>cell_calculator</artifactId>
    <version>1.2.0</version>
</dependency>
```

### 系统要求

- **Java**: 17 或更高版本
- **内存**: 建议 512MB 以上
- **CPU**: 支持多核并行计算

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd cell-calculator

# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package
```

## 📚 完整示例

### 电商销售分析示例

以下是一个完整的电商销售数据分析示例，展示了 Cell Calculator 的各种功能：

```java
public class EcommerceSalesAnalysis {
    public static void main(String[] args) throws IOException {
        CellCalculator calculator = new CellCalculator();

        // 基础销售数据
        calculator.set("A1", "'产品'");
        calculator.set("B1", "'单价'");
        calculator.set("C1", "'数量'");
        calculator.set("D1", "'销售额'");
        calculator.set("E1", "'利润率'");
        calculator.set("F1", "'利润'");

        // 产品数据
        calculator.set("A2", "'手机'");
        calculator.set("B2", "2999");
        calculator.set("C2", "150");
        calculator.set("D2", "=B2*C2");
        calculator.set("E2", "0.25");
        calculator.set("F2", "=D2*E2");

        calculator.set("A3", "'平板'");
        calculator.set("B3", "1999");
        calculator.set("C3", "80");
        calculator.set("D3", "=B3*C3");
        calculator.set("E3", "0.30");
        calculator.set("F3", "=D3*E3");

        calculator.set("A4", "'耳机'");
        calculator.set("B4", "299");
        calculator.set("C4", "500");
        calculator.set("D4", "=B4*C4");
        calculator.set("E4", "0.40");
        calculator.set("F4", "=D4*E4");

        // 汇总统计
        calculator.set("A6", "'总销售额'");
        calculator.set("D6", "=SUM(D2:D4)");

        calculator.set("A7", "'总利润'");
        calculator.set("F7", "=SUM(F2:F4)");

        calculator.set("A8", "'平均利润率'");
        calculator.set("F8", "=F7/D6");

        calculator.set("A9", "'最佳产品'");
        calculator.set("F9", "=IF(F2>F3, IF(F2>F4, A2, A4), IF(F3>F4, A3, A4))");

        // 输出结果
        System.out.println("=== 电商销售分析结果 ===");
        System.out.println("手机销售额: " + calculator.get("D2"));
        System.out.println("平板销售额: " + calculator.get("D3"));
        System.out.println("耳机销售额: " + calculator.get("D4"));
        System.out.println("总销售额: " + calculator.get("D6"));
        System.out.println("总利润: " + calculator.get("F7"));
        System.out.println("平均利润率: " + String.format("%.2f%%",
            ((Double)calculator.get("F8")) * 100));
        System.out.println("最佳产品: " + calculator.get("F9"));

        // 导出依赖关系图
        SvgExporter exporter = new SvgExporter(calculator);
        try (FileOutputStream output = new FileOutputStream("sales_analysis.svg")) {
            exporter.export(output);
            System.out.println("\n依赖关系图已导出: sales_analysis.svg");
        }

        calculator.shutdown();
    }
}
```

### 财务报表示例

```java
public class FinancialReport {
    public static void main(String[] args) throws IOException {
        CellCalculator calculator = new CellCalculator();

        // 收入项目
        calculator.set("A1", "'营业收入'");
        calculator.set("B1", "1000000");

        calculator.set("A2", "'其他收入'");
        calculator.set("B2", "50000");

        calculator.set("A3", "'总收入'");
        calculator.set("B3", "=B1+B2");

        // 成本项目
        calculator.set("A5", "'营业成本'");
        calculator.set("B5", "600000");

        calculator.set("A6", "'管理费用'");
        calculator.set("B6", "150000");

        calculator.set("A7", "'销售费用'");
        calculator.set("B7", "100000");

        calculator.set("A8", "'总成本'");
        calculator.set("B8", "=B5+B6+B7");

        // 利润计算
        calculator.set("A10", "'毛利润'");
        calculator.set("B10", "=B3-B5");

        calculator.set("A11", "'净利润'");
        calculator.set("B11", "=B3-B8");

        calculator.set("A12", "'利润率'");
        calculator.set("B12", "=B11/B3");

        // 财务指标
        calculator.set("A14", "'毛利率'");
        calculator.set("B14", "=B10/B3");

        calculator.set("A15", "'成本率'");
        calculator.set("B15", "=B8/B3");

        // 输出报表
        System.out.println("=== 财务报表 ===");
        System.out.println("总收入: ¥" + String.format("%,.0f", calculator.get("B3")));
        System.out.println("总成本: ¥" + String.format("%,.0f", calculator.get("B8")));
        System.out.println("毛利润: ¥" + String.format("%,.0f", calculator.get("B10")));
        System.out.println("净利润: ¥" + String.format("%,.0f", calculator.get("B11")));
        System.out.println("毛利率: " + String.format("%.1f%%",
            ((Double)calculator.get("B14")) * 100));
        System.out.println("净利率: " + String.format("%.1f%%",
            ((Double)calculator.get("B12")) * 100));

        // 导出可视化图表
        SvgExporter exporter = new SvgExporter(calculator);
        try (FileOutputStream output = new FileOutputStream("financial_report.svg")) {
            exporter.export(output);
            System.out.println("\n财务报表依赖图已导出: financial_report.svg");
        }

        calculator.shutdown();
    }
}
```

### 学生成绩管理示例

```java
public class StudentGradeManager {
    public static void main(String[] args) throws IOException {
        CellCalculator calculator = new CellCalculator();

        // 表头
        calculator.set("A1", "'姓名'");
        calculator.set("B1", "'数学'");
        calculator.set("C1", "'英语'");
        calculator.set("D1", "'物理'");
        calculator.set("E1", "'总分'");
        calculator.set("F1", "'平均分'");
        calculator.set("G1", "'等级'");

        // 学生数据
        String[] students = {"张三", "李四", "王五", "赵六"};
        int[][] scores = {
            {85, 92, 78},
            {76, 88, 82},
            {94, 85, 91},
            {68, 75, 72}
        };

        for (int i = 0; i < students.length; i++) {
            int row = i + 2;
            calculator.set("A" + row, "'" + students[i] + "'");
            calculator.set("B" + row, String.valueOf(scores[i][0]));
            calculator.set("C" + row, String.valueOf(scores[i][1]));
            calculator.set("D" + row, String.valueOf(scores[i][2]));
            calculator.set("E" + row, "=B" + row + "+C" + row + "+D" + row);
            calculator.set("F" + row, "=E" + row + "/3");
            calculator.set("G" + row, "=IF(F" + row + ">=90, '优秀', " +
                "IF(F" + row + ">=80, '良好', " +
                "IF(F" + row + ">=70, '中等', '需要改进')))");
        }

        // 统计信息
        calculator.set("A7", "'班级统计'");
        calculator.set("A8", "'数学平均'");
        calculator.set("B8", "=AVG(B2:B5)");
        calculator.set("A9", "'英语平均'");
        calculator.set("C9", "=AVG(C2:C5)");
        calculator.set("A10", "'物理平均'");
        calculator.set("D10", "=AVG(D2:D5)");
        calculator.set("A11", "'最高总分'");
        calculator.set("E11", "=MAX(E2:E5)");
        calculator.set("A12", "'最低总分'");
        calculator.set("E12", "=MIN(E2:E5)");

        // 输出成绩报告
        System.out.println("=== 学生成绩报告 ===");
        for (int i = 2; i <= 5; i++) {
            System.out.printf("%s: 总分%.0f, 平均%.1f, 等级%s%n",
                calculator.get("A" + i),
                calculator.get("E" + i),
                calculator.get("F" + i),
                calculator.get("G" + i));
        }

        System.out.println("\n=== 班级统计 ===");
        System.out.printf("数学平均: %.1f%n", calculator.get("B8"));
        System.out.printf("英语平均: %.1f%n", calculator.get("C9"));
        System.out.printf("物理平均: %.1f%n", calculator.get("D10"));
        System.out.printf("最高总分: %.0f%n", calculator.get("E11"));
        System.out.printf("最低总分: %.0f%n", calculator.get("E12"));

        // 导出成绩分析图
        CellCalculatorSvgExporter exporter = new CellCalculatorSvgExporter(calculator);
        try (FileOutputStream output = new FileOutputStream("grade_analysis.svg")) {
            exporter.exportToSvg(output);
            System.out.println("\n成绩分析图已导出: grade_analysis.svg");
        }

        calculator.shutdown();
    }
}
```

## 🔍 测试和示例

### 运行测试

````bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=CellCalculatorTest


## 🎨 SVG 可视化导出

### 功能特性

Cell Calculator 提供强大的 SVG 可视化导出功能，将复杂的单元格依赖关系转换为直观的图形：

- **🎯 智能布局**: 使用 Sugiyama 算法进行层次化布局，最小化边的交叉
- **🌈 颜色分组**: 根据依赖层级和目标单元格自动分配颜色，相同目标的依赖使用相同颜色
- **✨ 交互效果**: 支持鼠标悬停效果，箭头会变粗变暗
- **📊 多种样式**: 不同类型的单元格使用不同的背景色（数值、公式、字符串、错误）
- **🔍 详细信息**: 鼠标悬停显示单元格的完整定义和值

### 颜色分组规则

SVG 导出器会根据以下规则为依赖箭头分配颜色：

1. **按层级分组**: 相同层级的依赖关系会被分组处理
2. **按目标分组**: 指向同一目标单元格的所有箭头使用相同颜色
3. **预定义色彩**: 使用 15 种预定义的鲜明颜色，循环使用
4. **悬停效果**: 鼠标悬停时箭头颜色会自动变暗，宽度增加

### 使用示例

```java
// 创建复杂的依赖关系
CellCalculator calculator = new CellCalculator();

// 基础数据层
calculator.set("A1", "100");
calculator.set("B1", "200");
calculator.set("C1", "50");

// 计算层
calculator.set("A2", "=A1*1.2");
calculator.set("B2", "=B1*1.5");
calculator.set("C2", "=C1*2");

// 汇总层
calculator.set("D1", "=A2+B2");
calculator.set("D2", "=C2+D1");

// 导出SVG
CellCalculatorSvgExporter exporter = new CellCalculatorSvgExporter(calculator);
try (FileOutputStream output = new FileOutputStream("dependency_graph.svg")) {
    exporter.exportToSvg(output);
}
````

## 📄 许可证

Apache License 2.0

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📈 更新日志

### v1.2.0 (最新)

- ✨ 新增 SVG 可视化导出功能
- 🎨 实现智能颜色分组，相同目标依赖使用相同颜色
- 🎯 集成 Sugiyama 算法进行层次化布局
- ✨ 添加交互效果，支持鼠标悬停
- 📊 支持多种单元格样式（数值、公式、字符串、错误）
- 🔍 添加详细的工具提示信息

### v1.1.0

- 🚀 性能优化，提升大规模数据处理能力
- 🔒 增强线程安全性
- 🛡️ 改进错误处理机制
- 📝 完善文档和示例

### v1.0.0

- 🎉 初始版本发布
- 🧮 支持基本的公式计算和依赖管理
- ⚡ 实现增量计算和缓存机制
- 🔗 添加线程安全支持
- 📚 提供完整的函数库

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 📧 Email: jsuper1980@msn.com
- 💬 GitHub Issues: [提交问题](https://github.com/jsuper1980/CellCalculator/issues)

---

本项目采用 Apache-2.0 许可证开源，您可以在遵守许可证条款的前提下自由使用、修改和分发本项目的代码。

---
