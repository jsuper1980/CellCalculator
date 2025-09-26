package j2.basic.utils.calc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CellCalculator的SVG导出辅助类
 * 用于将内存中的表格数据以美观的SVG格式输出，直观展示单元格之间的依赖关系
 * 
 * @author j2
 * @version 1.0
 */
public class SvgExporter {

  // SVG样式常量
  private static final int CELL_WIDTH = 120;
  private static final int CELL_HEIGHT = 60;
  private static final int CELL_MARGIN_X = 40;
  private static final int CELL_MARGIN_Y = 40;
  private static final int GRID_SPACING_X = CELL_WIDTH + CELL_MARGIN_X;
  private static final int GRID_SPACING_Y = CELL_HEIGHT + CELL_MARGIN_Y;
  private static final int PADDING = 50;

  // 颜色主题
  private static final String COLOR_CELL_NORMAL = "#E3F2FD";
  private static final String COLOR_CELL_FORMULA = "#FFF3E0";
  private static final String COLOR_CELL_STRING = "#F3E5F5";
  private static final String COLOR_CELL_ERROR = "#FFEBEE";
  private static final String COLOR_BORDER = "#1976D2";
  private static final String COLOR_TEXT = "#212121";
  private static final String COLOR_ARROW = "#FF5722";
  private static final String COLOR_ARROW_HOVER = "#D32F2F";

  // 颜色分配相关常量
  private static final String[] PREDEFINED_COLORS = {
      "#FF5722", "#2196F3", "#4CAF50", "#FF9800", "#9C27B0",
      "#F44336", "#00BCD4", "#8BC34A", "#FFC107", "#673AB7",
      "#E91E63", "#009688", "#CDDC39", "#FF6F00", "#3F51B5"
  };

  private final CellCalculator calculator;

  /**
   * 单元格位置信息
   */
  private static class CellPosition {
    final String cellId;
    final int x, y;
    final int gridX, gridY;

    CellPosition(String cellId, int gridX, int gridY) {
      this.cellId = cellId;
      this.gridX = gridX;
      this.gridY = gridY;
      this.x = PADDING + gridX * GRID_SPACING_X;
      this.y = PADDING + gridY * GRID_SPACING_Y;
    }

    int getCenterX() {
      return x + CELL_WIDTH / 2;
    }

    int getCenterY() {
      return y + CELL_HEIGHT / 2;
    }
  }

  /**
   * 依赖关系信息
   */
  private static class Dependency {
    final String from;
    final String to;

    Dependency(String from, String to) {
      this.from = from;
      this.to = to;
    }
  }

  public SvgExporter(CellCalculator calculator) {
    this.calculator = calculator;
  }

  /**
   * 将表格数据导出为SVG格式
   * 
   * @param outputStream 输出流
   * @throws IOException 如果写入失败
   */
  public void export(OutputStream outputStream) throws IOException {
    StampedLock lock = getCalculatorLock();
    long stamp = lock.readLock();
    try {
      Map<String, Cell> cells = getCalculatorCells();
      if (cells.isEmpty()) {
        writeEmptySvg(outputStream);
        return;
      }

      // 计算布局
      Map<String, CellPosition> positions = calculateLayout(cells.keySet());
      List<Dependency> dependencies = extractDependencies(cells);

      // 生成SVG
      generateSvg(outputStream, cells, positions, dependencies);

    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * 计算单元格布局
   * 使用Sugiyama算法进行DAG层次化布局，最小化边的交叉
   */
  private Map<String, CellPosition> calculateLayout(Set<String> cellIds) {
    if (cellIds.isEmpty()) {
      return new HashMap<>();
    }

    // 构建依赖图
    Map<String, Set<String>> dependencyGraph = buildDependencyGraph(cellIds);

    // 如果没有依赖关系，使用简单网格布局
    if (dependencyGraph.values().stream().allMatch(Set::isEmpty)) {
      return calculateSimpleGridLayout(cellIds);
    }

    // 使用Sugiyama算法进行层次化布局
    return calculateSugiyamaLayout(cellIds, dependencyGraph);
  }

  /**
   * 构建依赖关系图
   */
  private Map<String, Set<String>> buildDependencyGraph(Set<String> cellIds) {
    Map<String, Set<String>> graph = new HashMap<>();

    // 初始化所有节点
    for (String cellId : cellIds) {
      graph.put(cellId, new HashSet<>());
    }

    // 获取单元格数据并提取依赖关系
    try {
      Map<String, Cell> cells = getCalculatorCells();
      Pattern cellRefPattern = Pattern.compile("\\b([A-Z]+\\d+)\\b");

      for (String cellId : cellIds) {
        Cell cell = cells.get(cellId);
        if (cell != null && cell.getDefine() != null && cell.getDefine().startsWith("=")) {
          Matcher matcher = cellRefPattern.matcher(cell.getDefine());
          while (matcher.find()) {
            String referencedCell = matcher.group(1);
            if (cellIds.contains(referencedCell)) {
              graph.get(referencedCell).add(cellId); // referencedCell -> cellId
            }
          }
        }
      }
    } catch (Exception e) {
      // 如果获取依赖关系失败，返回空图
      return graph;
    }

    return graph;
  }

  /**
   * 简单网格布局（用于无依赖关系的情况）
   */
  private Map<String, CellPosition> calculateSimpleGridLayout(Set<String> cellIds) {
    Map<String, CellPosition> positions = new HashMap<>();
    List<String> sortedIds = new ArrayList<>(cellIds);
    sortedIds.sort(String::compareTo);

    int cols = (int) Math.ceil(Math.sqrt(sortedIds.size()));
    for (int i = 0; i < sortedIds.size(); i++) {
      int gridX = i % cols;
      int gridY = i / cols;
      positions.put(sortedIds.get(i), new CellPosition(sortedIds.get(i), gridX, gridY));
    }

    return positions;
  }

  /**
   * Sugiyama算法布局
   */
  private Map<String, CellPosition> calculateSugiyamaLayout(Set<String> cellIds, Map<String, Set<String>> dependencyGraph) {
    // 第一步：层次分配（拓扑排序）
    Map<String, Integer> layers = assignLayers(cellIds, dependencyGraph);

    // 第二步：交叉最小化
    Map<Integer, List<String>> layerNodes = groupNodesByLayer(layers);
    minimizeCrossings(layerNodes, dependencyGraph);

    // 第三步：坐标分配
    return assignCoordinates(layerNodes);
  }

  /**
   * 层次分配：使用拓扑排序将节点分配到不同层次
   */
  private Map<String, Integer> assignLayers(Set<String> cellIds, Map<String, Set<String>> dependencyGraph) {
    Map<String, Integer> layers = new HashMap<>();
    Map<String, Integer> inDegree = new HashMap<>();

    // 计算入度
    for (String cellId : cellIds) {
      inDegree.put(cellId, 0);
    }
    for (String from : dependencyGraph.keySet()) {
      for (String to : dependencyGraph.get(from)) {
        inDegree.put(to, inDegree.get(to) + 1);
      }
    }

    // 拓扑排序
    Queue<String> queue = new LinkedList<>();
    for (String cellId : cellIds) {
      if (inDegree.get(cellId) == 0) {
        queue.offer(cellId);
        layers.put(cellId, 0);
      }
    }

    while (!queue.isEmpty()) {
      String current = queue.poll();
      int currentLayer = layers.get(current);

      for (String next : dependencyGraph.get(current)) {
        inDegree.put(next, inDegree.get(next) - 1);
        if (inDegree.get(next) == 0) {
          queue.offer(next);
          layers.put(next, currentLayer + 1);
        }
      }
    }

    return layers;
  }

  /**
   * 按层次分组节点
   */
  private Map<Integer, List<String>> groupNodesByLayer(Map<String, Integer> layers) {
    Map<Integer, List<String>> layerNodes = new HashMap<>();

    for (Map.Entry<String, Integer> entry : layers.entrySet()) {
      int layer = entry.getValue();
      layerNodes.computeIfAbsent(layer, k -> new ArrayList<>()).add(entry.getKey());
    }

    // 按字母顺序排序每层的节点
    for (List<String> nodes : layerNodes.values()) {
      nodes.sort(String::compareTo);
    }

    return layerNodes;
  }

  /**
   * 交叉最小化：使用重心启发式算法减少边的交叉
   */
  private void minimizeCrossings(Map<Integer, List<String>> layerNodes, Map<String, Set<String>> dependencyGraph) {
    int maxLayer = layerNodes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    // 多次迭代优化
    for (int iteration = 0; iteration < 3; iteration++) {
      // 从上到下
      for (int layer = 1; layer <= maxLayer; layer++) {
        if (layerNodes.containsKey(layer)) {
          optimizeLayerOrder(layerNodes, layer, dependencyGraph, true);
        }
      }

      // 从下到上
      for (int layer = maxLayer - 1; layer >= 0; layer--) {
        if (layerNodes.containsKey(layer)) {
          optimizeLayerOrder(layerNodes, layer, dependencyGraph, false);
        }
      }
    }
  }

  /**
   * 优化单层节点顺序
   */
  private void optimizeLayerOrder(Map<Integer, List<String>> layerNodes, int layer,
      Map<String, Set<String>> dependencyGraph, boolean downward) {
    List<String> currentLayer = layerNodes.get(layer);
    if (currentLayer.size() <= 1)
      return;

    // 计算每个节点的重心位置
    Map<String, Double> barycenters = new HashMap<>();

    for (String node : currentLayer) {
      List<Integer> connectedPositions = new ArrayList<>();

      if (downward && layerNodes.containsKey(layer - 1)) {
        // 向下优化：查看上一层的连接
        List<String> prevLayer = layerNodes.get(layer - 1);
        for (int i = 0; i < prevLayer.size(); i++) {
          if (dependencyGraph.get(prevLayer.get(i)).contains(node)) {
            connectedPositions.add(i);
          }
        }
      } else if (!downward && layerNodes.containsKey(layer + 1)) {
        // 向上优化：查看下一层的连接
        List<String> nextLayer = layerNodes.get(layer + 1);
        for (String target : dependencyGraph.get(node)) {
          int pos = nextLayer.indexOf(target);
          if (pos >= 0) {
            connectedPositions.add(pos);
          }
        }
      }

      // 计算重心
      double barycenter = connectedPositions.isEmpty() ? currentLayer.indexOf(node)
          : connectedPositions.stream().mapToInt(Integer::intValue).average().orElse(0);
      barycenters.put(node, barycenter);
    }

    // 按重心排序
    currentLayer.sort((a, b) -> Double.compare(barycenters.get(a), barycenters.get(b)));
  }

  /**
   * 坐标分配：为每个节点分配最终的坐标位置
   */
  private Map<String, CellPosition> assignCoordinates(Map<Integer, List<String>> layerNodes) {
    Map<String, CellPosition> positions = new HashMap<>();

    for (Map.Entry<Integer, List<String>> entry : layerNodes.entrySet()) {
      int layer = entry.getKey();
      List<String> nodes = entry.getValue();

      for (int i = 0; i < nodes.size(); i++) {
        String cellId = nodes.get(i);
        positions.put(cellId, new CellPosition(cellId, i, layer));
      }
    }

    return positions;
  }

  /**
   * 解析单元格ID
   */
  private static class ParsedCellId {
    final String cellId;
    final String column;
    final int row;

    ParsedCellId(String cellId, String column, int row) {
      this.cellId = cellId;
      this.column = column;
      this.row = row;
    }
  }

  private ParsedCellId parseCellId(String cellId) {
    Pattern pattern = Pattern.compile("^([A-Z]+)(\\d+)$");
    Matcher matcher = pattern.matcher(cellId);
    if (matcher.matches()) {
      String column = matcher.group(1);
      int row = Integer.parseInt(matcher.group(2));
      return new ParsedCellId(cellId, column, row);
    }
    return null;
  }

  /**
   * 提取依赖关系
   */
  private List<Dependency> extractDependencies(Map<String, Cell> cells) {
    List<Dependency> dependencies = new ArrayList<>();
    Pattern cellRefPattern = Pattern.compile("\\b([A-Z]+\\d+)\\b");

    for (Map.Entry<String, Cell> entry : cells.entrySet()) {
      String cellId = entry.getKey();
      String define = entry.getValue().getDefine();

      if (define != null && define.startsWith("=")) {
        Matcher matcher = cellRefPattern.matcher(define);
        while (matcher.find()) {
          String referencedCell = matcher.group(1);
          if (cells.containsKey(referencedCell)) {
            dependencies.add(new Dependency(referencedCell, cellId));
          }
        }
      }
    }

    return dependencies;
  }

  /**
   * 生成SVG内容
   */
  private void generateSvg(OutputStream outputStream, Map<String, Cell> cells,
      Map<String, CellPosition> positions, List<Dependency> dependencies) throws IOException {

    Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

    // 计算SVG尺寸
    int maxX = positions.values().stream().mapToInt(p -> p.x).max().orElse(0);
    int maxY = positions.values().stream().mapToInt(p -> p.y).max().orElse(0);
    int svgWidth = maxX + CELL_WIDTH + PADDING;
    int svgHeight = maxY + CELL_HEIGHT + PADDING;

    // 分配箭头颜色
    Map<String, String> arrowColors = assignArrowColors(dependencies, positions);

    // SVG头部
    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    writer.write(String.format("<svg width=\"%d\" height=\"%d\" xmlns=\"http://www.w3.org/2000/svg\">\n",
        svgWidth, svgHeight));

    // 添加样式定义
    writeSvgStyles(writer, arrowColors);

    // 添加标题
    writer.write("  <text x=\"" + (svgWidth / 2) + "\" y=\"30\" class=\"title\">单元格依赖关系图</text>\n");

    // 绘制依赖关系箭头（先绘制，避免被单元格覆盖）
    writeDependencyArrows(writer, dependencies, positions, arrowColors);

    // 绘制单元格
    writeCells(writer, cells, positions);

    // SVG尾部
    writer.write("</svg>\n");
    writer.flush();
  }

  /**
   * 写入SVG样式定义
   */
  private void writeSvgStyles(Writer writer, Map<String, String> arrowColors) throws IOException {
    writer.write("  <defs>\n");
    writer.write("    <style type=\"text/css\"><![CDATA[\n");
    writer.write(
        "      .title { font-family: 'Microsoft YaHei', Arial, sans-serif; font-size: 18px; font-weight: bold; text-anchor: middle; fill: "
            + COLOR_TEXT + "; }\n");
    writer.write("      .cell-normal { fill: " + COLOR_CELL_NORMAL + "; stroke: " + COLOR_BORDER + "; stroke-width: 2; }\n");
    writer.write("      .cell-formula { fill: " + COLOR_CELL_FORMULA + "; stroke: " + COLOR_BORDER + "; stroke-width: 2; }\n");
    writer.write("      .cell-string { fill: " + COLOR_CELL_STRING + "; stroke: " + COLOR_BORDER + "; stroke-width: 2; }\n");
    writer.write("      .cell-error { fill: " + COLOR_CELL_ERROR + "; stroke: " + COLOR_BORDER + "; stroke-width: 2; }\n");
    writer.write("      .cell-text { font-family: 'Consolas', 'Monaco', monospace; font-size: 11px; fill: " + COLOR_TEXT
        + "; text-anchor: middle; }\n");
    writer.write("      .cell-id { font-family: 'Microsoft YaHei', Arial, sans-serif; font-size: 12px; font-weight: bold; fill: "
        + COLOR_BORDER + "; text-anchor: middle; }\n");

    // 默认箭头样式
    writer.write("      .arrow { stroke: " + COLOR_ARROW + "; stroke-width: 2; fill: none; marker-end: url(#arrowhead); }\n");
    writer.write("      .arrow:hover { stroke: " + COLOR_ARROW_HOVER + "; stroke-width: 3; }\n");
    writer.write("      .arrow-curve { stroke: " + COLOR_ARROW + "; stroke-width: 2; fill: none; }\n");
    writer.write("      .arrow-curve:hover { stroke: " + COLOR_ARROW_HOVER + "; stroke-width: 3; }\n");
    writer.write("      .arrow-head { fill: " + COLOR_ARROW + "; stroke: none; }\n");
    writer.write("      .arrow-head:hover { fill: " + COLOR_ARROW_HOVER + "; }\n");

    // 动态生成颜色样式
    Set<String> processedColors = new HashSet<>();
    for (Map.Entry<String, String> entry : arrowColors.entrySet()) {
      String key = entry.getKey();
      String colorClass = entry.getValue();

      if (!key.endsWith(":hover") && !processedColors.contains(colorClass)) {
        processedColors.add(colorClass);

        // 从类名中提取颜色值
        String colorValue = "#" + colorClass.substring("arrow-color-".length()).toUpperCase();
        String hoverColor = generateHoverColor(colorValue);

        // 为每种颜色生成CSS类
        writer.write("      ." + colorClass + " { stroke: " + colorValue + "; stroke-width: 2; fill: none; }\n");
        writer.write("      ." + colorClass + ":hover { stroke: " + hoverColor + "; stroke-width: 3; }\n");
        writer.write("      ." + colorClass + "-head { fill: " + colorValue + "; stroke: none; }\n");
        writer.write("      ." + colorClass + "-head:hover { fill: " + hoverColor + "; }\n");
      }
    }

    writer.write("      .cell-rect:hover { stroke-width: 3; filter: brightness(1.1); }\n");
    writer.write("    ]]></style>\n");

    // 箭头标记定义
    writer.write("    <marker id=\"arrowhead\" markerWidth=\"10\" markerHeight=\"7\" refX=\"9\" refY=\"3.5\" orient=\"auto\">\n");
    writer.write("      <polygon points=\"0 0, 10 3.5, 0 7\" fill=\"" + COLOR_ARROW + "\" />\n");
    writer.write("    </marker>\n");
    writer.write("  </defs>\n");
  }

  /**
   * 绘制依赖关系箭头
   */
  /**
   * 绘制依赖关系箭头
   * 针对层次化布局优化箭头绘制，支持曲线箭头以减少视觉混乱
   */
  private void writeDependencyArrows(Writer writer, List<Dependency> dependencies,
      Map<String, CellPosition> positions, Map<String, String> arrowColors) throws IOException {
    for (Dependency dep : dependencies) {
      CellPosition fromPos = positions.get(dep.from);
      CellPosition toPos = positions.get(dep.to);

      if (fromPos != null && toPos != null) {
        // 检查是否为层次化布局（垂直方向的依赖）
        boolean isHierarchical = Math.abs(fromPos.gridY - toPos.gridY) > 0;

        if (isHierarchical && fromPos.gridY < toPos.gridY) {
          // 层次化布局：绘制优化的曲线箭头
          drawHierarchicalArrow(writer, fromPos, toPos, dep, arrowColors);
        } else {
          // 普通布局：绘制直线箭头
          drawStraightArrow(writer, fromPos, toPos, dep, arrowColors);
        }
      }
    }
  }

  /**
   * 绘制层次化布局的曲线箭头
   */
  private void drawHierarchicalArrow(Writer writer, CellPosition fromPos, CellPosition toPos, Dependency dep,
      Map<String, String> arrowColors) throws IOException {
    int fromCenterX = fromPos.getCenterX();
    int fromCenterY = fromPos.getCenterY();
    int toCenterX = toPos.getCenterX();
    int toCenterY = toPos.getCenterY();

    // 计算起点和终点（从单元格边界开始和结束）
    int startX = fromCenterX;
    int startY = fromPos.y + CELL_HEIGHT; // 从底部出发
    int endX = toCenterX;
    int endY = toPos.y; // 到顶部结束

    // 计算控制点以创建平滑的贝塞尔曲线
    int controlY1 = startY + (endY - startY) / 3;
    int controlY2 = endY - (endY - startY) / 3;

    // 如果水平距离较大，添加水平控制点
    int horizontalOffset = Math.abs(endX - startX);
    int controlX1 = startX;
    int controlX2 = endX;

    if (horizontalOffset > GRID_SPACING_X) {
      controlX1 = startX + (endX - startX) / 4;
      controlX2 = endX - (endX - startX) / 4;
    }

    // 获取箭头颜色
    String arrowKey = dep.from + "->" + dep.to;
    String colorClass = arrowColors.getOrDefault(arrowKey, "arrow-curve");

    // 绘制贝塞尔曲线路径
    writer.write(String.format("  <path d=\"M %d %d Q %d %d %d %d T %d %d\" class=\"%s\">\n",
        startX, startY, controlX1, controlY1, (startX + endX) / 2, (controlY1 + controlY2) / 2, endX, endY, colorClass));
    writer.write("    <title>" + escapeXml(dep.from + " → " + dep.to) + "</title>\n");
    writer.write("  </path>\n");

    // 绘制箭头头部
    drawArrowHead(writer, endX, endY, startX, startY, colorClass);
  }

  /**
   * 绘制直线箭头（用于非层次化布局）
   */
  private void drawStraightArrow(Writer writer, CellPosition fromPos, CellPosition toPos, Dependency dep, Map<String, String> arrowColors)
      throws IOException {
    // 计算箭头起点和终点（避免与单元格重叠）
    int[] startPoint = calculateArrowPoint(fromPos, toPos, true);
    int[] endPoint = calculateArrowPoint(toPos, fromPos, false);

    // 获取箭头颜色
    String arrowKey = dep.from + "->" + dep.to;
    String colorClass = arrowColors.getOrDefault(arrowKey, "arrow");

    writer.write(String.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" class=\"%s\">\n",
        startPoint[0], startPoint[1], endPoint[0], endPoint[1], colorClass));
    writer.write("    <title>" + escapeXml(dep.from + " → " + dep.to) + "</title>\n");
    writer.write("  </line>\n");

    // 绘制箭头头部
    drawArrowHead(writer, endPoint[0], endPoint[1], startPoint[0], startPoint[1], colorClass);
  }

  /**
   * 绘制箭头头部
   */
  private void drawArrowHead(Writer writer, int endX, int endY, int startX, int startY, String colorClass) throws IOException {
    // 计算箭头方向
    double dx = endX - startX;
    double dy = endY - startY;
    double length = Math.sqrt(dx * dx + dy * dy);

    if (length == 0)
      return;

    // 单位向量
    double unitX = dx / length;
    double unitY = dy / length;

    // 箭头头部大小
    int arrowSize = 8;
    double arrowAngle = Math.PI / 6; // 30度

    // 计算箭头头部的两个点
    double cos = Math.cos(arrowAngle);
    double sin = Math.sin(arrowAngle);

    int arrowX1 = (int) (endX - arrowSize * (unitX * cos + unitY * sin));
    int arrowY1 = (int) (endY - arrowSize * (unitY * cos - unitX * sin));
    int arrowX2 = (int) (endX - arrowSize * (unitX * cos - unitY * sin));
    int arrowY2 = (int) (endY - arrowSize * (unitY * cos + unitX * sin));

    // 绘制箭头头部，使用对应的颜色类
    String headClass = colorClass.replace("arrow", "arrow-head");
    writer.write(String.format("  <polygon points=\"%d,%d %d,%d %d,%d\" class=\"%s\"/>\n",
        endX, endY, arrowX1, arrowY1, arrowX2, arrowY2, headClass));
  }

  /**
   * 计算箭头与单元格边界的交点
   */
  private int[] calculateArrowPoint(CellPosition cellPos, CellPosition targetPos, boolean isStart) {
    int centerX = cellPos.getCenterX();
    int centerY = cellPos.getCenterY();
    int targetCenterX = targetPos.getCenterX();
    int targetCenterY = targetPos.getCenterY();

    // 计算方向向量
    double dx = targetCenterX - centerX;
    double dy = targetCenterY - centerY;
    double length = Math.sqrt(dx * dx + dy * dy);

    if (length == 0)
      return new int[] {centerX, centerY};

    // 单位向量
    double unitX = dx / length;
    double unitY = dy / length;

    // 计算与矩形边界的交点
    double halfWidth = CELL_WIDTH / 2.0;
    double halfHeight = CELL_HEIGHT / 2.0;

    double t;
    if (Math.abs(unitX) > Math.abs(unitY)) {
      // 与左右边界相交
      t = (unitX > 0 ? halfWidth : -halfWidth) / unitX;
    } else {
      // 与上下边界相交
      t = (unitY > 0 ? halfHeight : -halfHeight) / unitY;
    }

    int pointX = (int) (centerX + t * unitX);
    int pointY = (int) (centerY + t * unitY);

    return new int[] {pointX, pointY};
  }

  /**
   * 绘制单元格
   */
  private void writeCells(Writer writer, Map<String, Cell> cells, Map<String, CellPosition> positions) throws IOException {
    for (Map.Entry<String, Cell> entry : cells.entrySet()) {
      String cellId = entry.getKey();
      Cell cell = entry.getValue();
      CellPosition pos = positions.get(cellId);

      if (pos != null) {
        writeSingleCell(writer, cellId, cell, pos);
      }
    }
  }

  /**
   * 绘制单个单元格
   */
  private void writeSingleCell(Writer writer, String cellId, Cell cell, CellPosition pos) throws IOException {
    String define = cell.getDefine();
    Object value = cell.getValue();
    String error = cell.getError();

    // 确定单元格类型和样式
    String cellClass;
    if (error != null) {
      cellClass = "cell-error";
    } else if (define != null && define.startsWith("=")) {
      cellClass = "cell-formula";
    } else if (define != null && define.startsWith("'")) {
      cellClass = "cell-string";
    } else {
      cellClass = "cell-normal";
    }

    // 绘制单元格矩形
    writer.write(String.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"%s cell-rect\">\n",
        pos.x, pos.y, CELL_WIDTH, CELL_HEIGHT, cellClass));
    writer.write("    <title>" + escapeXml(cellId + ": " + (define != null ? define : "")) + "</title>\n");
    writer.write("  </rect>\n");

    // 绘制单元格ID
    writer.write(String.format("  <text x=\"%d\" y=\"%d\" class=\"cell-id\">%s</text>\n",
        pos.getCenterX(), pos.y + 18, escapeXml(cellId)));

    // 绘制单元格内容
    String displayText;
    if (error != null) {
      displayText = "ERROR";
    } else if (value != null) {
      displayText = truncateText(value.toString(), 15);
    } else if (define != null) {
      displayText = truncateText(define, 15);
    } else {
      displayText = "";
    }

    writer.write(String.format("  <text x=\"%d\" y=\"%d\" class=\"cell-text\">%s</text>\n",
        pos.getCenterX(), pos.getCenterY() + 4, escapeXml(displayText)));

    // 如果有定义，在下方显示定义内容
    if (define != null && !define.equals(displayText)) {
      String truncatedDefine = truncateText(define, 18);
      writer.write(String.format("  <text x=\"%d\" y=\"%d\" class=\"cell-text\" style=\"font-size: 9px; opacity: 0.7;\">%s</text>\n",
          pos.getCenterX(), pos.y + CELL_HEIGHT - 8, escapeXml(truncatedDefine)));
    }
  }

  /**
   * 截断文本以适应单元格显示
   */
  private String truncateText(String text, int maxLength) {
    if (text == null)
      return "";
    if (text.length() <= maxLength)
      return text;
    return text.substring(0, maxLength - 3) + "...";
  }

  /**
   * XML转义
   */
  private String escapeXml(String text) {
    if (text == null)
      return "";
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * 写入空SVG（当没有单元格时）
   */
  private void writeEmptySvg(OutputStream outputStream) throws IOException {
    Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    writer.write("<svg width=\"400\" height=\"200\" xmlns=\"http://www.w3.org/2000/svg\">\n");
    writer.write(
        "  <text x=\"200\" y=\"100\" style=\"font-family: 'Microsoft YaHei', Arial, sans-serif; font-size: 16px; text-anchor: middle; fill: #666;\">暂无单元格数据</text>\n");
    writer.write("</svg>\n");
    writer.flush();
  }

  /**
   * 获取计算器的锁（通过反射访问私有字段）
   */
  private StampedLock getCalculatorLock() {
    try {
      java.lang.reflect.Field lockField = CellCalculator.class.getDeclaredField("lock");
      lockField.setAccessible(true);
      return (StampedLock) lockField.get(calculator);
    } catch (Exception e) {
      throw new RuntimeException("无法访问CellCalculator的lock字段", e);
    }
  }

  /**
   * 获取计算器的单元格映射（通过反射访问私有字段）
   */
  @SuppressWarnings("unchecked")
  private Map<String, Cell> getCalculatorCells() {
    try {
      java.lang.reflect.Field cellsField = CellCalculator.class.getDeclaredField("cells");
      cellsField.setAccessible(true);
      return (Map<String, Cell>) cellsField.get(calculator);
    } catch (Exception e) {
      throw new RuntimeException("无法访问CellCalculator的cells字段", e);
    }
  }

  /**
   * 为依赖关系分配颜色，同一层级中目标相同的连线使用相同颜色
   */
  private Map<String, String> assignArrowColors(List<Dependency> dependencies, Map<String, CellPosition> positions) {
    Map<String, String> arrowColors = new HashMap<>();

    // 按层级分组依赖关系
    Map<Integer, List<Dependency>> dependenciesByLayer = new HashMap<>();
    for (Dependency dep : dependencies) {
      CellPosition fromPos = positions.get(dep.from);
      CellPosition toPos = positions.get(dep.to);

      if (fromPos != null && toPos != null) {
        // 使用起始单元格的层级作为分组依据
        int layer = fromPos.gridY;
        dependenciesByLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(dep);
      }
    }

    // 为每个层级分配颜色
    for (Map.Entry<Integer, List<Dependency>> entry : dependenciesByLayer.entrySet()) {
      List<Dependency> layerDeps = entry.getValue();

      // 按目标单元格分组
      Map<String, List<Dependency>> depsByTarget = new HashMap<>();
      for (Dependency dep : layerDeps) {
        depsByTarget.computeIfAbsent(dep.to, k -> new ArrayList<>()).add(dep);
      }

      // 为每个目标分配颜色
      int colorIndex = 0;
      for (Map.Entry<String, List<Dependency>> targetEntry : depsByTarget.entrySet()) {
        String color = PREDEFINED_COLORS[colorIndex % PREDEFINED_COLORS.length];
        // 生成CSS类名（去掉#号，转换为小写）
        String colorClass = "arrow-color-" + color.substring(1).toLowerCase();

        for (Dependency dep : targetEntry.getValue()) {
          String key = dep.from + "->" + dep.to;
          arrowColors.put(key, colorClass);
        }

        colorIndex++;
      }
    }

    return arrowColors;
  }

  /**
   * 生成悬停时的颜色（稍微变暗）
   */
  private String generateHoverColor(String color) {
    // 简单的颜色变暗算法
    if (color.startsWith("#") && color.length() == 7) {
      try {
        int rgb = Integer.parseInt(color.substring(1), 16);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // 降低亮度
        r = Math.max(0, (int) (r * 0.8));
        g = Math.max(0, (int) (g * 0.8));
        b = Math.max(0, (int) (b * 0.8));

        return String.format("#%02X%02X%02X", r, g, b);
      } catch (NumberFormatException e) {
        return COLOR_ARROW_HOVER; // 回退到默认颜色
      }
    }
    return COLOR_ARROW_HOVER;
  }
}
