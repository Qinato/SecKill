package com.dan.seckill.service.impl;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.dan.seckill.dao.SeckillDao;
import com.dan.seckill.dao.SuccessKilledDao;
import com.dan.seckill.dto.Exposer;
import com.dan.seckill.dto.SeckillExecution;
import com.dan.seckill.entity.Seckill;
import com.dan.seckill.entity.SuccessKilled;
import com.dan.seckill.enums.SeckillStateEnum;
import com.dan.seckill.exception.RepeatKillException;
import com.dan.seckill.exception.SeckillCloseException;
import com.dan.seckill.exception.SeckillException;
import com.dan.seckill.service.SeckillService;

@Service
public class SeckillServiceImpl implements SeckillService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private SeckillDao seckillDao;
	
	@Autowired
	private SuccessKilledDao successKilledDao;
	
	//md5盐值字符串，用于混淆MD5
	private final String salt = ";asdrweBWN@@#^&^%%RFDSv@#%……&&……%4";
	
	public List<Seckill> getSeckillList() {
		return seckillDao.queryAll(0, 4);
	}

	public Seckill getById(long seckillId) {
		return seckillDao.queryById(seckillId);
	}

	public Exposer exportSeckillUrl(long seckillId) {
		Seckill seckill = seckillDao.queryById(seckillId);
		if (seckill == null) {
			return new Exposer(false, seckillId);
		}
		Date startTime = seckill.getStartTime();
		Date endTime = seckill.getEndTime();
		//系统当前时间
		Date nowTime = new Date();
		if (nowTime.getTime() < startTime.getTime() || nowTime.getTime() > endTime.getTime()) {
			return new Exposer(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
		}
		//转化特定字符串的过程，不可逆
		String md5 = getMD5(seckillId);
		return new Exposer(true, md5, seckillId);
	}
	
	private String getMD5(long seckillId) {
		String base = seckillId + "/" + salt;
		String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
		return md5;
	}

	public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
			throws SeckillException, RepeatKillException, SeckillCloseException {
		if (md5 == null || !md5.equals(getMD5(seckillId))) {
			throw new SeckillException("seckill data rewrite");
		}
		//执行秒杀逻辑：减库存+记录购买行为
		Date nowTime = new Date();
		
		try {
			//减库存
			int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
			if (updateCount <= 0) {
				//没有更新到记录，秒杀结束
				throw new SeckillCloseException("seckill is closed");
			}
			else {
				//记录购买行为
				int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
				//唯一：seckillId,userPhone
				if (insertCount <= 0) {
					throw new RepeatKillException("seckill repeated");
				}
				else {
					//秒杀成功
					SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
					return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
				}
			}
		} catch (SeckillCloseException e1) {
			throw e1;
		} catch (RepeatKillException e2) {
			throw e2;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			//所有编译期异常转化为运行期异常
			throw new SeckillException("seckill inner error:" + e.getMessage());
		}
	}

}




