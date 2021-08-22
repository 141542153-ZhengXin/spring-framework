package com.geek.model;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * @version V1.0
 * @description:
 * @author: geek
 * @date 2021/08/22
 **/
public class MyBeanPostProcessor implements BeanPostProcessor {
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Person) {
			Person person = (Person) bean;
			person.setName("zx1");
		}
		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}
}
