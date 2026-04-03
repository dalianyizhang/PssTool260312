package com.example.psstool260312.utils;

import java.util.*;

public class PlanGenerator {

    // 疲劳度定义
    private static final String[] FATIGUE_NAMES = {"大笑", "微笑", "流汗"};
    private static final float[] FATIGUE_VALUES = {1f, 0.5f, 0.3333f};

    // 属性列表（用于优先级排序）
    private static final List<String> COMMON_ATTRS = Arrays.asList(
            "血量", "攻击", "能力", "导航", "科技", "引擎", "武器", "维修"
    );
    private static final String ENDURANCE = "耐力";
    private static final String REPAIR = "维修";
    private static final String HP = "血量";

    // 物品定义
    private static class Item {
        String name;
        float mainFactor;
        float subFactor;
        boolean isEndurance;

        Item(String name, float main, float sub, boolean endurance) {
            this.name = name;
            this.mainFactor = main;
            this.subFactor = sub;
            this.isEndurance = endurance;
        }
    }

    // 药品列表（一级到四级，普通和耐力）
    private static final List<Item> MEDICINE_ITEMS = new ArrayList<Item>() {{
        add(new Item("一级药", 0.02f, 0.01f, false));
        add(new Item("二级药", 0.04f, 0.02f, false));
        add(new Item("三级药", 0.08f, 0.03f, false));
        add(new Item("四级药", 0.16f, 0.05f, false));
        add(new Item("一级耐力药", 0.08f, 0.01f, true));
        add(new Item("二级耐力药", 0.12f, 0.01f, true));
        add(new Item("三级耐力药", 0.16f, 0.02f, true));
        add(new Item("四级耐力药", 0.24f, 0.03f, true));
    }};

    // 训练列表
    private static final List<Item> TRAIN_ITEMS = new ArrayList<Item>() {{
        add(new Item("绿色训练", 0.04f, 0.01f, false));
        add(new Item("蓝色训练", 0.08f, 0.01f, false));
        add(new Item("金色训练", 0.12f, 0.02f, false));
        add(new Item("耐力绿训", 0.05f, 0f, true));
        add(new Item("耐力蓝训", 0.08f, 0.02f, true));
        add(new Item("耐力金训", 0.16f, 0.03f, true));
    }};


    private static Item findItem(String name) {
        for (Item item : MEDICINE_ITEMS) {
            if (item.name.equals(name)) return item;
        }
        return null;
    }

    // 成功率计算（返回百分比数值，如 0.5 表示 0.5%）
    private static float calcRate(int maxLimit, int totalTrain, int attrValue, float fatigueVal, float factor) {
        if (maxLimit <= totalTrain) return 0;
        return (maxLimit - totalTrain) * (maxLimit - attrValue) * fatigueVal * factor * 100f / (maxLimit * maxLimit);
    }

    // 判断是否可以使用该物品加该属性
    private static boolean canAdd(int maxLimit, int totalTrain, int attrValue, float fatigueVal,
                                  float mainFactor, float subFactor) {
        float mainRate = calcRate(maxLimit, totalTrain, attrValue, fatigueVal, mainFactor);
        float subRate = calcRate(maxLimit, totalTrain, 0, fatigueVal, subFactor);
        return mainRate >= 1 && subRate < 1;
    }

    ///////////////////

    // 策略对应的药品顺序（新增 avoidFive）
    private static List<Item> getMedicineOrder(String strategy) {
        List<Item> order = new ArrayList<>();
        if ("priorFour".equals(strategy)) {
            order.add(findItem("一级药"));
            order.add(findItem("二级药"));
            order.add(findItem("四级药"));
            order.add(findItem("三级药"));
            order.add(findItem("一级耐力药"));
            order.add(findItem("二级耐力药"));
            order.add(findItem("三级耐力药"));
            order.add(findItem("四级耐力药"));
        } else if ("saveFive".equals(strategy)) {
            order.add(findItem("一级药"));
            order.add(findItem("二级药"));
            order.add(findItem("三级药"));
            order.add(findItem("一级耐力药"));
            order.add(findItem("二级耐力药"));
            order.add(findItem("三级耐力药"));
            order.add(findItem("四级耐力药"));
            order.add(findItem("四级药"));
        } else if ("avoidFive".equals(strategy)) {
            // 避免五药策略：药品顺序与基础相同
            order.add(findItem("一级药"));
            order.add(findItem("二级药"));
            order.add(findItem("三级药"));
            order.add(findItem("一级耐力药"));
            order.add(findItem("二级耐力药"));
            order.add(findItem("三级耐力药"));
            order.add(findItem("四级耐力药"));
            order.add(findItem("四级药"));
        } else {
            // 基础顺序
            order.add(findItem("一级药"));
            order.add(findItem("二级药"));
            order.add(findItem("三级药"));
            order.add(findItem("一级耐力药"));
            order.add(findItem("二级耐力药"));
            order.add(findItem("三级耐力药"));
            order.add(findItem("四级耐力药"));
            order.add(findItem("四级药"));
        }
        return order;
    }

    // 获取属性列表（针对 avoidFive 策略调整顺序）
    private static List<String> getAttrList(Map<String, Integer> cur, Map<String, Integer> target,
                                            Item item, String strategy, String currentFocusAttr) {
        List<String> result = new ArrayList<>();
        if (item.isEndurance) {
            // 耐力物品只能加耐力
            if (cur.getOrDefault(ENDURANCE, 0) < target.getOrDefault(ENDURANCE, 0)) {
                result.add(ENDURANCE);
            }
        } else {
            if ("avoidFive".equals(strategy)) {
                // 避免五药策略：优先处理血量、维修
                // 先添加血量（如果未达标）
                if (cur.getOrDefault(HP, 0) < target.getOrDefault(HP, 0)) {
                    result.add(HP);
                }
                // 再添加维修（如果未达标）
                if (cur.getOrDefault(REPAIR, 0) < target.getOrDefault(REPAIR, 0)) {
                    result.add(REPAIR);
                }
                // 最后添加其他普通属性（按原顺序）
                for (String attr : COMMON_ATTRS) {
                    if (!attr.equals(HP) && !attr.equals(REPAIR) &&
                            cur.getOrDefault(attr, 0) < target.getOrDefault(attr, 0)) {
                        result.add(attr);
                    }
                }
            } else {
                // 原逻辑：普通属性按顺序，维修放最后
                for (String attr : COMMON_ATTRS) {
                    if (cur.getOrDefault(attr, 0) < target.getOrDefault(attr, 0)) {
                        result.add(attr);
                    }
                }
                // 将维修移到末尾
                result.remove(REPAIR);
                if (cur.getOrDefault(REPAIR, 0) < target.getOrDefault(REPAIR, 0)) {
                    result.add(REPAIR);
                }
            }
        }

        // 最省五药策略：锁定最低属性
        if ("saveFive".equals(strategy) && currentFocusAttr != null) {
            if (result.contains(currentFocusAttr)) {
                result = new ArrayList<>(Collections.singletonList(currentFocusAttr));
            } else {
                result = new ArrayList<>(); // 锁定属性已达标，本轮无可用属性
            }
        }
        return result;
    }

    // 五级药保底优先级（所有策略共用，避免耐力/血量/维修）
    private static String getFiveDrugAttr(Map<String, Integer> cur, Map<String, Integer> target) {
        // 优先非耐力、非血量、非维修的属性
        for (String attr : target.keySet()) {
            if (!attr.equals(ENDURANCE) && !attr.equals(HP) && !attr.equals(REPAIR) &&
                    cur.getOrDefault(attr, 0) < target.get(attr)) {
                return attr;
            }
        }
        // 其次血量
        if (cur.getOrDefault(HP, 0) < target.getOrDefault(HP, 0)) {
            return HP;
        }
        // 再其次维修
        if (cur.getOrDefault(REPAIR, 0) < target.getOrDefault(REPAIR, 0)) {
            return REPAIR;
        }
        // 最后耐力
        if (cur.getOrDefault(ENDURANCE, 0) < target.getOrDefault(ENDURANCE, 0)) {
            return ENDURANCE;
        }
        return null;
    }

    ////////////////////////







    // 计算最低属性（差值最大）
    private static String getLowestAttr(Map<String, Integer> cur, Map<String, Integer> target) {
        String lowest = null;
        int maxGap = -1;
        for (String attr : target.keySet()) {
            int gap = target.get(attr) - cur.getOrDefault(attr, 0);
            if (gap > maxGap) {
                maxGap = gap;
                lowest = attr;
            }
        }
        return lowest;
    }


    // 记录加点操作
    private static void addPoint(Map<String, Integer> cur, String attr, int delta,
                                 List<String> steps, String fatigue, String itemName,
                                 Map<String, Integer> fiveDrugCount, boolean isFiveDrug) {
        int oldVal = cur.getOrDefault(attr, 0);
        int newVal = oldVal + delta;
        cur.put(attr, newVal);
        if (isFiveDrug) {
            fiveDrugCount.put(attr, fiveDrugCount.getOrDefault(attr, 0) + 1);
            steps.add(String.format("[脸黑] - 五级药 - %s %d→%d", attr, oldVal, newVal));
        } else {
            steps.add(String.format("[%s] - %s - %s %d→%d", fatigue, itemName, attr, oldVal, newVal));
        }
    }

    // 主方法：生成培养计划
    public static PlanResult generatePlan(String type, String strategy, int maxLimit,
                                          Map<String, Integer> current,
                                          Map<String, Integer> target) {
        Map<String, Integer> cur = new HashMap<>(current);
        int totalTrain = cur.values().stream().mapToInt(Integer::intValue).sum();
        List<String> steps = new ArrayList<>();
        Map<String, Integer> fiveDrugCount = new HashMap<>();

        // 第一点特殊处理：微笑疲劳度，根据方案类型决定优先顺序
        if (totalTrain == 0 && !target.isEmpty()) {
            String firstAttr = null;
            for (String attr : target.keySet()) {
                if (cur.getOrDefault(attr, 0) < target.get(attr)) {
                    firstAttr = attr;
                    break;
                }
            }
            if (firstAttr != null) {
                boolean isEndurance = ENDURANCE.equals(firstAttr);
                String trainName = isEndurance ? "耐力绿训" : "绿色训练";
                String drugName = isEndurance ? "一级耐力药" : "一级药";

                // 查找训练物品
                Item trainItem = null;
                for (Item item : TRAIN_ITEMS) {
                    if (item.name.equals(trainName)) {
                        trainItem = item;
                        break;
                    }
                }
                // 查找药品物品
                Item drugItem = findItem(drugName);

                boolean success = false;

                if ("train".equals(type)) {
                    // 训练方案：先尝试训练，后尝试药物
                    if (trainItem != null && canAdd(maxLimit, totalTrain, cur.getOrDefault(firstAttr, 0),
                            0.5f, trainItem.mainFactor, trainItem.subFactor)) {
                        addPoint(cur, firstAttr, 1, steps, "微笑", trainName, fiveDrugCount, false);
                        totalTrain++;
                        success = true;
                    } else if (drugItem != null && canAdd(maxLimit, totalTrain, cur.getOrDefault(firstAttr, 0),
                            0.5f, drugItem.mainFactor, drugItem.subFactor)) {
                        addPoint(cur, firstAttr, 1, steps, "微笑", drugName, fiveDrugCount, false);
                        totalTrain++;
                        success = true;
                    }
                } else {
                    // 药物方案：先尝试药物，后尝试训练
                    if (drugItem != null && canAdd(maxLimit, totalTrain, cur.getOrDefault(firstAttr, 0),
                            0.5f, drugItem.mainFactor, drugItem.subFactor)) {
                        addPoint(cur, firstAttr, 1, steps, "微笑", drugName, fiveDrugCount, false);
                        totalTrain++;
                        success = true;
                    } else if (trainItem != null && canAdd(maxLimit, totalTrain, cur.getOrDefault(firstAttr, 0),
                            0.5f, trainItem.mainFactor, trainItem.subFactor)) {
                        addPoint(cur, firstAttr, 1, steps, "微笑", trainName, fiveDrugCount, false);
                        totalTrain++;
                        success = true;
                    }
                }
                // 如果都不成功，跳过第一点，让主循环处理
            }
        }

        // 根据类型初始化物品列表和阶段标志
        List<Item> itemList;
        boolean trainingPhase = false;
        if ("train".equals(type)) {
            itemList = new ArrayList<>(TRAIN_ITEMS);
            trainingPhase = true;
        } else {
            itemList = getMedicineOrder(strategy);
        }

        String currentFocusAttr = null; // 最省五药策略的锁定属性

        // 主循环：直到所有属性达标或总训练点达到上限
        while (true) {
            // 检查是否全部达标
            boolean allFinished = true;
            for (Map.Entry<String, Integer> entry : target.entrySet()) {
                if (cur.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    allFinished = false;
                    break;
                }
            }
            if (allFinished || totalTrain >= maxLimit) break;

            boolean success = false;

            // 尝试所有疲劳度
            for (int fIdx = 0; fIdx < FATIGUE_NAMES.length && !success; fIdx++) {
                String fatigueName = FATIGUE_NAMES[fIdx];
                float fatigueVal = FATIGUE_VALUES[fIdx];

                // 尝试当前物品列表
                for (Item item : itemList) {
                    if (success) break;

                    // 获取可加属性列表（按优先级排序）
                    List<String> attrList = getAttrList(cur, target, item, strategy, currentFocusAttr);
                    if (attrList.isEmpty()) continue;

                    for (String attr : attrList) {
                        if (cur.getOrDefault(attr, 0) >= target.getOrDefault(attr, 0)) continue;

                        float mainFactor = item.mainFactor;
                        float subFactor = item.subFactor;
                        if (canAdd(maxLimit, totalTrain, cur.get(attr), fatigueVal, mainFactor, subFactor)) {
                            addPoint(cur, attr, 1, steps, fatigueName, item.name, fiveDrugCount, false);
                            totalTrain++;
                            success = true;

                            // 最省五药策略：如果当前锁定的属性达标，重新计算最低属性
                            if ("saveFive".equals(strategy) && currentFocusAttr != null &&
                                    cur.get(currentFocusAttr) >= target.get(currentFocusAttr)) {
                                currentFocusAttr = getLowestAttr(cur, target);
                            }
                            break;
                        }
                    }
                }
            }

            if (!success) {
                // 没有可用操作：使用五级药保底
                String attr = getFiveDrugAttr(cur, target);
                if (attr == null) break; // 无属性可加，理论上不会发生

                addPoint(cur, attr, 1, steps, null, null, fiveDrugCount, true);
                totalTrain++;

                // 如果是训练阶段且没有可用训练，则切换到药物阶段
                if (trainingPhase) {
                    itemList = getMedicineOrder("base"); // 基础吃药方案
                    trainingPhase = false;
                    currentFocusAttr = null; // 重置锁定属性，药物阶段可能用其他策略
                    // 如果切换后还是药物阶段，继续循环
                }
                // 对于最省五药策略，保底后可能影响锁定属性，重新计算
                if ("saveFive".equals(strategy)) {
                    currentFocusAttr = getLowestAttr(cur, target);
                }
            }
        }
        // 合并步骤（新增）
        List<String> mergedSteps = mergeSteps(steps);

        // 最终统计
        int fiveDrugTotal = fiveDrugCount.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Integer> finalValues = new HashMap<>(cur);

        // 返回时使用合并后的步骤
        return new PlanResult(mergedSteps, totalTrain, fiveDrugTotal, fiveDrugCount, finalValues);
    }

    // ================== 合并连续相同操作 ==================

    /**
     * 合并连续的相同操作（疲劳状态、道具、属性完全相同，且数值连续）
     * 例如：[大笑] - 绿色训练 - 血量 5→6 和 [大笑] - 绿色训练 - 血量 6→7 合并为 [大笑] - 绿色训练 - 血量 5→7
     *
     * @param steps 原始步骤列表
     * @return 合并后的步骤列表
     */
    private static List<String> mergeSteps(List<String> steps) {
        if (steps.isEmpty()) return steps;
        List<String> merged = new ArrayList<>();
        String lastStep = steps.get(0);
        int lastStart = extractStart(lastStep);
        int lastEnd = extractEnd(lastStep);
        String lastKey = extractKey(lastStep);

        for (int i = 1; i < steps.size(); i++) {
            String current = steps.get(i);
            String currentKey = extractKey(current);
            int currentStart = extractStart(current);
            int currentEnd = extractEnd(current);

            if (currentKey.equals(lastKey) && currentStart == lastEnd) {
                // 连续相同操作，扩展结束值
                lastEnd = currentEnd;
            } else {
                // 输出上一组
                merged.add(formatStep(lastKey, lastStart, lastEnd));
                // 重置为当前步骤
                lastStep = current;
                lastKey = currentKey;
                lastStart = currentStart;
                lastEnd = currentEnd;
            }
        }
        // 输出最后一组
        merged.add(formatStep(lastKey, lastStart, lastEnd));
        return merged;
    }

    /**
     * 提取步骤的关键部分（疲劳状态 + 道具 + 属性名），不含数值
     * 例如："[大笑] - 绿色训练 - 血量 5→6" 返回 "[大笑] - 绿色训练 - 血量"
     */
    private static String extractKey(String step) {
        int lastSpace = step.lastIndexOf(' ');
        if (lastSpace == -1) return step;
        return step.substring(0, lastSpace);
    }

    /**
     * 提取步骤中的起始值
     * 例如："[大笑] - 绿色训练 - 血量 5→6" 返回 5
     */
    private static int extractStart(String step) {
        int arrow = step.indexOf('→');
        if (arrow == -1) return 0;
        int space = step.lastIndexOf(' ', arrow);
        if (space == -1) return 0;
        try {
            return Integer.parseInt(step.substring(space + 1, arrow));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 提取步骤中的结束值
     * 例如："[大笑] - 绿色训练 - 血量 5→6" 返回 6
     */
    private static int extractEnd(String step) {
        int arrow = step.indexOf('→');
        if (arrow == -1) return 0;
        int space = step.indexOf(' ', arrow);
        if (space == -1) space = step.length();
        try {
            return Integer.parseInt(step.substring(arrow + 1, space));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 格式化合并后的步骤（不显示次数）
     * 例如：key = "[大笑] - 绿色训练 - 血量"，start=5, end=7 输出 "[大笑] - 绿色训练 - 血量 5→7"
     */
    private static String formatStep(String key, int start, int end) {
        return key + " " + start + "→" + end;
    }

    // 返回结果封装类
    public static class PlanResult {
        public List<String> steps;
        public int totalTrain;
        public int fiveDrugTotal;
        public Map<String, Integer> fiveDrugPerAttr;
        public Map<String, Integer> finalValues;

        public PlanResult(List<String> steps, int totalTrain, int fiveDrugTotal,
                          Map<String, Integer> fiveDrugPerAttr, Map<String, Integer> finalValues) {
            this.steps = steps;
            this.totalTrain = totalTrain;
            this.fiveDrugTotal = fiveDrugTotal;
            this.fiveDrugPerAttr = fiveDrugPerAttr;
            this.finalValues = finalValues;
        }
    }
}