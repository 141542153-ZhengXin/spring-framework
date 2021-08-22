package com.geek;

import com.geek.model.Person;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version V1.0
 * @description:
 * @author: geek
 * @date 2021/08/15
 **/
public class MainApplication {
	public static void main(String[] args) {
		AbstractApplicationContext ac = new ClassPathXmlApplicationContext("applicationContext.xml");
		Person person = ac.getBean(Person.class);
	}
}
