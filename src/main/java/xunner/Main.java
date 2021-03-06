package xunner;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import xunner.bean.*;
import xunner.enums.OrderState;
import xunner.mapper.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

/*
	操作内容：
	对某个用户进行套餐的查询（包括历史记录）、订购、退订（考虑立即生效和次月生效）操作
	某个用户在通话情况下的资费生成
	某个用户在使用流量情况下的资费生成
	某个用户月账单的生成
 */

/**
 * 调用演示类
 * <br>
 * created on 2018/10/24
 *
 * @author 巽
 **/
@SuppressWarnings("SameParameterValue")
public class Main {
	private static SqlSessionFactory sqlSessionFactory;

	public static void main(String[] args) {
		long startTime;
		double time;

		// 1. 订购：subscribe(用户id, 套餐id)
		startTime = System.currentTimeMillis();
		subscribe(1, 1);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("1. 订购用时：" + time + "秒");
		System.out.println();

		// 2. 退订（立即生效）：unsubscribeByNow(订单id)
		startTime = System.currentTimeMillis();
		unsubscribeByNow(5);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("2. 退订（立即生效）用时：" + time + "秒");
		System.out.println();

		// 3. 退订（次月生效）：unsubscribeNextMonth(订单id)
		startTime = System.currentTimeMillis();
		unsubscribeNextMonth(6);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("3. 退订（次月生效）用时：" + time + "秒");
		System.out.println();

		// 4. 查询套餐订购记录：searchUserOrders(用户id, 开始时间, 结束时间)
		startTime = System.currentTimeMillis();
		searchUserOrders(1, LocalDate.of(2018, Month.JANUARY, 1), LocalDate.now());
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("4. 查询套餐订购记录用时：" + time + "秒");
		System.out.println();

		// 5. 生成通话资费：generateCallExpense(用户id, 通话时长/分钟)
		startTime = System.currentTimeMillis();
		generateCallExpense(1, 3.0);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("5. 生成通话资费用时：" + time + "秒");
		System.out.println();

		// 6. 生成短信资费：generateMessageExpense(用户id)
		startTime = System.currentTimeMillis();
		generateMessageExpense(1);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("6. 生成短信资费用时：" + time + "秒");
		System.out.println();

		// 7. 生成流量资费：generateDataExpense(用户id, 流量大小/M, 是否为本地流量)
		startTime = System.currentTimeMillis();
		generateDataExpense(1, 9.9, true);
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("7. 生成流量资费用时：" + time + "秒");
		System.out.println();

		// 8. 生成月账单：generateMonthlyBill(用户id，时间（年月）)
		startTime = System.currentTimeMillis();
		generateMonthlyBill(1, LocalDate.now().plusMonths(1));
		time = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.println("8. 生成月账单用时：" + time + "秒");
		System.out.println();


//		DataGenerator.generatePlans(sqlSessionFactory);
//		DataGenerator.generateUsers(sqlSessionFactory);
//		DataGenerator.generateOrders(sqlSessionFactory, LocalDate.now());
//		DataGenerator.generateExpenses(sqlSessionFactory, 1, LocalDate.now());
//		DataGenerator.generateDataOfNovember(sqlSessionFactory);
	}

	/**
	 * 生成月账单
	 *
	 * @param userId 用户id
	 * @param date 时间（只使用年月）
	 */
	private static void generateMonthlyBill(int userId, LocalDate date) {
		LocalDate firstDayOfMonth = LocalDate.of(date.getYear(), date.getMonth(), 1);
		LocalDate lastDayOfMonth = LocalDate.of(date.getYear(), date.getMonth(), date.getDayOfMonth());
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);
			CallExpenseMapper callExpenseMapper = session.getMapper(CallExpenseMapper.class);
			DataExpenseMapper dataExpenseMapper = session.getMapper(DataExpenseMapper.class);
			MessageExpenseMapper messageExpenseMapper = session.getMapper(MessageExpenseMapper.class);

			List<Map<String, Object>> results = orderMapper.getValidOrdersMessages(userId, firstDayOfMonth, lastDayOfMonth);
			double sum = 0;

			System.out.println("———————————————————————————————————————月账单———————————————————————————————————————");
			System.out.println("时间：" + date.getYear() + "年" + date.getMonth().getValue() + "月");
			System.out.println("已订购套餐：");

			for (Map result : results) {
				OrderState state = (OrderState) result.get("state");
				Plan plan = (Plan) result.get("plan");
				int orderId = (int) result.get("orderId");
				int messagesLeft = plan.getMessage() - messageExpenseMapper.countMessagesInOrder(orderId);
				double minutesLeft = plan.getMinutes() - callExpenseMapper.sumMinutesInOrder(orderId);
				double localDataLeft = plan.getLocalData() - dataExpenseMapper.sumDataInOrder(orderId, true);
				double nationalDataLeft = plan.getNationalData() - dataExpenseMapper.sumDataInOrder(orderId, false);
				sum += plan.getPrice();

				System.out.println("\t名称: " + plan.getName()
						+ ", 价格: " + plan.getPrice()
						+ (plan.getMinutes() == 0 ? "" : ", 时长(剩/总): " + String.format("%.2f", minutesLeft) + "/" + plan.getMinutes())
						+ (plan.getMessage() == 0 ? "" : ", 短信(剩/总): " + messagesLeft + "/" + plan.getMessage())
						+ (plan.getLocalData() == 0 ? "" : ", 本地流量(剩/总): " + String.format("%.2f", localDataLeft) + "/" + plan.getLocalData())
						+ (plan.getNationalData() == 0 ? "" : ", 全国流量(剩/总): " + String.format("%.2f", nationalDataLeft) + "/" + plan.getNationalData())
						+ ", 状态: " + state.getValue());
			}
			System.out.println();

			LocalDateTime firstTimeOfMonth = LocalDateTime.of(firstDayOfMonth, LocalTime.MIN);
			LocalDateTime lastTimeOfMonth = LocalDateTime.of(lastDayOfMonth, LocalTime.MAX);
			double minutes = callExpenseMapper.sumMinutesWithoutOrder(userId, firstTimeOfMonth, lastTimeOfMonth);
			int message = messageExpenseMapper.countMessageWithoutOrder(userId, firstTimeOfMonth, lastTimeOfMonth);
			double localData = dataExpenseMapper.sumDataWithoutOrder(userId, true, firstTimeOfMonth, lastTimeOfMonth);
			double nationalData = dataExpenseMapper.sumDataWithoutOrder(userId, false, firstTimeOfMonth, lastTimeOfMonth);

			// 套餐外通话0.5元/分钟，短信0.1元/条，本地流量2元/M，全国流量5元/M
			sum += 0.5 * minutes + 0.1 * message + 2 * localData + 5 * nationalData;

			System.out.println("套餐外通话时长：" + String.format("%.2f", minutes) + "分钟，共计" + String.format("%.2f", 0.5 * minutes) + "元");
			System.out.println("套餐外短信：" + message + "条，共计" + String.format("%.2f", 0.1 * message) + "元");
			System.out.println("套餐外本地流量：" + String.format("%.2f", localData) + "M，共计" + String.format("%.2f", 2 * localData) + "元");
			System.out.println("套餐外全国流量：" + String.format("%.2f", nationalData) + "M，共计" + String.format("%.2f", 5 * nationalData) + "元");
			System.out.println();
			System.out.println("总计：" + sum + "元");
			System.out.println("—————————————————————————————————————————————————————————————————————————————————");

			session.commit();
		}
	}

	/**
	 * 生成流量资费
	 *
	 * @param userId  用户id
	 * @param data    流量大小
	 * @param isLocal 是否为本地流量（否则为全国流量）
	 */
	private static void generateDataExpense(int userId, double data, boolean isLocal) {
		LocalDate today = LocalDate.now();
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);
			DataExpenseMapper dataExpenseMapper = session.getMapper(DataExpenseMapper.class);

			List<Map<String, Object>> results = orderMapper.getValidOrdersIdAndTotal(userId,
					LocalDate.of(today.getYear(), today.getMonth(), 1),
					LocalDate.of(today.getYear(), today.getMonth(), today.getDayOfMonth()));
			double left = data;
			for (Map result : results) {    // 查询有无对应套餐流量可用
				int orderId = (int) result.get("orderId");
				double dataTotal = (double) result.get((isLocal ? "localData" : "nationalData"));
				double dataLeft = dataTotal - dataExpenseMapper.sumDataInOrder(orderId, isLocal);
				if (dataLeft > 0) {
					double dataToWrite = Math.min(dataLeft, left);
					DataExpense dataExpense = new DataExpense(userId, orderId, LocalDateTime.now(), dataToWrite, isLocal, 0);
					dataExpenseMapper.add(dataExpense);

					System.out.println("生成一笔流量资费：" + dataExpense);

					left -= dataToWrite;
					if (left <= 0) {
						break;
					}
				}
			}
			if (left != 0 && isLocal) { // 若本地流量套餐用尽，本地流量找不到可用套餐：查询可用的全国流量套餐
				for (Map result : results) {
					int orderId = (int) result.get("orderId");
					double dataTotal = (double) result.get(("nationalData"));
					double dataLeft = dataTotal - dataExpenseMapper.sumDataInOrder(orderId, false);
					if (dataLeft > 0) {
						double dataToWrite = Math.min(dataLeft, left);
						DataExpense dataExpense = new DataExpense(userId, orderId, LocalDateTime.now(), dataToWrite, false, 0);
						dataExpenseMapper.add(dataExpense);

						System.out.println("生成一笔流量资费：" + dataExpense);

						left -= dataToWrite;
						if (left <= 0) {
							break;
						}
					}
				}
			}
			if (left != 0) {
				// 本地2元/M，全国5元/M
				double cost = (isLocal ? 2 : 5) * left;
				DataExpense dataExpense = new DataExpense(userId, null, LocalDateTime.now(), left, isLocal, cost);
				dataExpenseMapper.add(dataExpense);

				// 扣钱
				UserMapper userMapper = session.getMapper(UserMapper.class);
				User user = userMapper.getById(userId);
				user.setBalance(user.getBalance() - cost);
				userMapper.update(user);

				System.out.println("生成一笔流量资费：" + dataExpense);
			}

			session.commit();
		}
	}

	/**
	 * 生成（一条）短信资费
	 *
	 * @param userId 用户id
	 */
	private static void generateMessageExpense(int userId) {
		LocalDate today = LocalDate.now();
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);
			MessageExpenseMapper messageExpenseMapper = session.getMapper(MessageExpenseMapper.class);

			List<Map<String, Object>> results = orderMapper.getValidOrdersIdAndTotal(userId,
					LocalDate.of(today.getYear(), today.getMonth(), 1),
					LocalDate.of(today.getYear(), today.getMonth(), today.getDayOfMonth()));
			boolean found = false;
			for (Map result : results) {
				int orderId = (int) result.get("orderId");
				int message = (int) result.get("message");
				int messagesLeft = message - messageExpenseMapper.countMessagesInOrder(orderId);
				if (messagesLeft > 0) {
					found = true;
					MessageExpense messageExpense = new MessageExpense(userId, orderId, LocalDateTime.now(), 0);
					messageExpenseMapper.add(messageExpense);

					System.out.println("生成一笔短信资费：" + messageExpense);

					break;
				}
			}
			if (!found) {
				// 0.1元/条
				MessageExpense messageExpense = new MessageExpense(userId, null, LocalDateTime.now(), 0.1);
				messageExpenseMapper.add(messageExpense);

				// 扣钱
				UserMapper userMapper = session.getMapper(UserMapper.class);
				User user = userMapper.getById(userId);
				user.setBalance(user.getBalance() - 0.1);
				userMapper.update(user);

				System.out.println("生成一笔短信资费：" + messageExpense);
			}

			session.commit();
		}
	}

	/**
	 * 生成通话资费
	 *
	 * @param userId  用户id
	 * @param minutes 通话时长（分钟）
	 */
	private static void generateCallExpense(int userId, double minutes) {
		LocalDate today = LocalDate.now();
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);
			CallExpenseMapper callExpenseMapper = session.getMapper(CallExpenseMapper.class);

			List<Map<String, Object>> results = orderMapper.getValidOrdersIdAndTotal(userId,
					LocalDate.of(today.getYear(), today.getMonth(), 1),
					LocalDate.of(today.getYear(), today.getMonth(), today.getDayOfMonth()));
			double left = minutes;
			for (Map result : results) {
				int orderId = (int) result.get("orderId");
				double minutesTotal = (double) result.get("minutes");
				double minutesLeft = minutesTotal - callExpenseMapper.sumMinutesInOrder(orderId);
				if (minutesLeft > 0) {
					double minuteToWrite = Math.min(minutesLeft, left);
					CallExpense callExpense = new CallExpense(userId, orderId, LocalDateTime.now(), minuteToWrite, 0);
					callExpenseMapper.add(callExpense);

					System.out.println("生成一笔通话资费：" + callExpense);

					left -= minuteToWrite;
					if (left <= 0) {
						break;
					}
				}
			}
			if (left != 0) {
				// 0.5元/分钟
				double cost = 0.5 * left;
				CallExpense callExpense = new CallExpense(userId, null, LocalDateTime.now(), left, cost);
				callExpenseMapper.add(callExpense);

				// 扣钱
				UserMapper userMapper = session.getMapper(UserMapper.class);
				User user = userMapper.getById(userId);
				user.setBalance(user.getBalance() - cost);
				userMapper.update(user);

				System.out.println("生成一笔通话资费：" + callExpense);

			}

			session.commit();
		}
	}

	/**
	 * 退订套餐，立即生效。逻辑：套餐内已使用的部分不再变动，未使用部分不予退款；若订单非当月订单则退订失败
	 *
	 * @param orderId 订单id
	 */
	private static void unsubscribeByNow(int orderId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			LocalDate today = LocalDate.now();
			Order order = orderMapper.getById(orderId);
			if ((order.getState() == OrderState.EFFECTIVE || order.getState() == OrderState.INVALID_NEXT_MONTH) &&
					order.getDate().getYear() == today.getYear() && order.getDate().getMonth() == today.getMonth()) {
				order.setState(OrderState.INVALID);
				orderMapper.update(order);

				System.out.println("退订成功：" + order);
			} else {
				System.out.println("订单已失效，退订失败");
			}

			session.commit();
		}
	}

	/**
	 * 退订套餐，次月生效；若订单非当月订单则退订失败
	 *
	 * @param orderId 订单id
	 */
	private static void unsubscribeNextMonth(int orderId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			LocalDate today = LocalDate.now();
			Order order = orderMapper.getById(orderId);
			if (order.getState() == OrderState.EFFECTIVE &&
					order.getDate().getYear() == today.getYear() && order.getDate().getMonth() == today.getMonth()) {
				order.setState(OrderState.INVALID_NEXT_MONTH);
				orderMapper.update(order);

				System.out.println("退订成功：" + order);
			} else {
				System.out.println("订单已失效，退订失败");
			}

			session.commit();
		}
	}

	/**
	 * 订购套餐
	 *
	 * @param userId 用户id
	 * @param planId 套餐id
	 */
	private static void subscribe(int userId, int planId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			UserMapper userMapper = session.getMapper(UserMapper.class);
			PlanMapper planMapper = session.getMapper(PlanMapper.class);

			User user = userMapper.getById(userId);
			Plan plan = planMapper.getById(planId);
			if (user.getBalance() < plan.getPrice()) {
				System.out.println("账户余额不足！");
			} else {
				OrderMapper orderMapper = session.getMapper(OrderMapper.class);
				Order order = new Order(userId, planId, LocalDate.now(), OrderState.EFFECTIVE);
				orderMapper.add(order);

				user.setBalance(user.getBalance() - plan.getPrice());
				userMapper.update(user);

				System.out.println("订购成功：" + order);
			}

			session.commit();
		}
	}

	/**
	 * 查询用户时间段内订购的套餐
	 *
	 * @param userId    用户id
	 * @param startDate 开始时间（年月）
	 * @param endDate   结束时间（年月）
	 */
	private static void searchUserOrders(int userId, LocalDate startDate, LocalDate endDate) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			List<Map<String, Object>> results = orderMapper.getOrdersByUserIdAndDates(userId, startDate, endDate);
			System.out.println("————————————————————————————————套餐订购记录————————————————————————————————");
			for (Map result : results) {
				OrderState state = (OrderState) result.get("state");
				LocalDate date = (LocalDate) result.get("date");
				Plan plan = (Plan) result.get("plan");
				System.out.println("名称: " + plan.getName()
						+ ", 价格: " + plan.getPrice()
						+ ", 日期: " + date
						+ ", 状态: " + state.getValue());
			}
			System.out.println("——————————————————————————————————————————————————————————————————————");

			session.commit();
		}
	}

	static {
		String resource = "mybatis-config.xml";
		try {
			InputStream inputStream = Resources.getResourceAsStream(resource);
			sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
			try (SqlSession session = sqlSessionFactory.openSession()) {
				UserMapper userMapper = session.getMapper(UserMapper.class);
				userMapper.getById(1);
				session.commit();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}