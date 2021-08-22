package com.geek.model;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * @version V1.0
 * @description:
 * @author: geek
 * @date 2021/08/15
 **/
public class Person implements BeanNameAware, EnvironmentAware {

	private String id;
	private String name;
	private String beanName;
	private Environment environment;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}
}
