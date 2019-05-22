package com.ywh.eurekahi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EurekaHiApplicationTests {

	@Test
	public void contextLoads() throws Exception {
	}

	@Autowired
	private RedisTemplate redisTemplate;

	@Test
	public void test1(){
		redisTemplate.opsForValue().set("ywh","ywh");
	}

}
