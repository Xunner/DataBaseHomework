package xunner;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import xunner.bean.Order;
import xunner.bean.Plan;
import xunner.bean.User;
import xunner.enums.OrderState;
import xunner.mapper.OrderMapper;
import xunner.mapper.PlanMapper;
import xunner.mapper.UserMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * 调用演示类
 * <br>
 * created on 2018/10/24
 *
 * @author 巽
 **/
public class Main {
	private static SqlSessionFactory sqlSessionFactory;

	public static void main(String[] args) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			UserMapper userMapper = session.getMapper(UserMapper.class);
			PlanMapper planMapper = session.getMapper(PlanMapper.class);
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			subscribe(2, 1);

			session.commit();
		}
	}

	/*
		对某个用户进行套餐的查询（包括历史记录）、订购、退订（考虑立即生效和次月生效）操作
		某个用户在通话情况下的资费生成
		某个用户在使用流量情况下的资费生成
		某个用户月账单的生成
	 */

	/**
	 * 生成月账单
	 *
	 * @param userId 用户id
	 * @param date   日期（年月）
	 */
	private static void generateMonthlyBill(int userId, LocalDate date) {
		//TODO
	}

	/**
	 * 生成流量资费
	 *
	 * @param userId  用户id
	 * @param data    流量大小
	 * @param isLocal 是否为本地流量（否则为全国流量）
	 */
	private static void generateDataExpense(int userId, double data, boolean isLocal) {
		//TODO
	}

	/**
	 * 生成（一条）短信资费
	 *
	 * @param userId 用户id
	 */
	private static void generateMessageExpense(int userId) {
		//TODO
	}

	/**
	 * 生成通话资费
	 *
	 * @param userId  用户id
	 * @param minutes 通话时长（分钟）
	 */
	private static void generateCallExpense(int userId, double minutes) {
		//TODO
	}

	/**
	 * 退订套餐，立即生效。逻辑：
	 *
	 * @param orderId 订单id
	 */
	private static void unsubscribeByNow(int orderId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			Order order = orderMapper.getById(orderId);
			if (order.getState() == OrderState.EFFECTIVE) {
				order.setState(OrderState.INVALID);
				orderMapper.update(order);
				// TODO
			} else {
				System.out.println("订单已过期");
			}

			session.commit();
		}
	}

	/**
	 * 退订套餐，次月生效
	 *
	 * @param orderId 订单id
	 */
	private static void unsubscribeNextMonth(int orderId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			OrderMapper orderMapper = session.getMapper(OrderMapper.class);

			Order order = orderMapper.getById(orderId);
			if (order.getState() == OrderState.EFFECTIVE) {
				order.setState(OrderState.INVALID_NEXT_MONTH);
				orderMapper.update(order);
			} else {
				System.out.println("订单已过期");
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
				orderMapper.add(new Order(userId, planId, LocalDate.now(), OrderState.EFFECTIVE));

				user.setBalance(user.getBalance() - plan.getPrice());
				userMapper.update(user);
			}

			session.commit();
		}
	}

	/**
	 * 查询用户当前生效中的套餐
	 *
	 * @param userId 用户id
	 */
	private static void searchUserCurrentOrders(int userId) {
		// TODO
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
			PlanMapper planMapper = session.getMapper(PlanMapper.class);

			List<Order> orders = orderMapper.getOrdersByUserIdAndDates(userId, startDate, endDate);
			System.out.println("名称\t\t\t价格\t\t\t日期\t\t\t状态");
			for (Order order : orders) {
				Plan plan = planMapper.getById(order.getPlanId());
				System.out.println(plan.getName() + "\t\t\t" + plan.getPrice() + "\t\t\t" + order.getDate() + "\t\t\t" + order.getState().getValue());
				// TODO
			}

			session.commit();
		}
	}

	static {
		String resource = "mybatis-config.xml";
		try {
			InputStream inputStream = Resources.getResourceAsStream(resource);
			sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}