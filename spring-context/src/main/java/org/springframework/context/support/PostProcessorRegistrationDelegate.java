/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}

	/**
	 * 1.如果当前 Spring 应用上下文是 BeanDefinitionRegistry 类型,则执行当前 Spring 应用上下文中所有
	 * BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor 的处理,以及底层 BeanFactory 容器中
	 * BeanDefinitionRegistryPostProcessor 的处理,处理顺序如下:
	 * 		1.当前 Spring 应用上下文中所有 BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry
	 * 		2.底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry
	 * 		（优先级:PriorityOrdered > Ordered > 无）
	 * 		3.当前 Spring 应用上下文和底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor#postProcessBeanFactory
	 * 		4.当前 Spring 应用上下文中所有 BeanFactoryPostProcessor#postProcessBeanFactory
	 * 2.否则，执行当前 Spring 应用上下文中所有 BeanFactoryPostProcessor#postProcessBeanFactory
	 * 3.执行底层 BeanFactory 容器中所有 BeanFactoryPostProcessor#postProcessBeanFactory,上面已经处理过的会跳过,
	 * 执行顺序和上面一样:PriorityOrdered > Ordered > 无
	 * 总结：有序地执行所有 BeanFactoryPostProcessor（包括 BeanDefinitionRegistryPostProcessor）处理器
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<>();

		// <1> 执行当前 Spring 应用上下文和底层 BeanFactory 容器中的 BeanFactoryPostProcessor、BeanDefinitionRegistryPostProcessor 们的处理
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// <1.1> 先遍历当前 Spring 应用上下文中的 `beanFactoryPostProcessors`,如果是 BeanDefinitionRegistryPostProcessor 类型则进行处理
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 执行
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 添加,以供后续执行其他 `postProcessBeanFactory(registry)` 方法
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 临时变量，用于临时保存 BeanFactory 容器中的 BeanDefinitionRegistryPostProcessor 对象
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// <1.2> 获取底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor 类型的 Bean 们,遍历进行处理
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 如果实现了 PriorityOrdered 接口,则获取到对应的 Bean
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 初始化
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			// 排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 临时保存起来
			registryProcessors.addAll(currentRegistryProcessors);
			// 执行
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			// 清理
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// <1.3> 获取底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor 类型的 Bean 们,遍历进行处理
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 如果实现了 Ordered 接口并且没有执行过,则获取到对应的 Bean
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 初始化
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			// 排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 临时保存起来
			registryProcessors.addAll(currentRegistryProcessors);
			// 执行
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			// 清理
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// <1.4> 获取底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor 类型的 Bean 们,遍历进行处理
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 如果该 BeanDefinitionRegistryPostProcessors 在上述过程中没有执行过,则获取到对应的 Bean
					if (!processedBeans.contains(ppName)) {
						// 初始化
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				// 排序
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 临时保存起来
				registryProcessors.addAll(currentRegistryProcessors);
				// 执行
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				// 清理
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			/*
			 * <1.5> 上述执行完当前 Spring 应用上下文和底层 BeanFactory 容器中所有 BeanDefinitionRegistryPostProcessor 处理器中的
			 * postProcessBeanDefinitionRegistry(registry) 方法后,接下来执行它们的 postProcessBeanFactory(beanFactory) 方法
			 *
			 * 注意:BeanDefinitionRegistryPostProcessor 继承 BeanFactoryPostProcessor 接口
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			/*
			 * <1.6> 这里我们执行当前 Spring 应用上下文中 BeanFactoryPostProcessor 处理器（非 BeanDefinitionRegistryPostProcessors 类型）的
			 * postProcessBeanFactory(beanFactory) 方法
			 *
			 * 例如：PropertyPlaceholderConfigurer、PropertySourcesPlaceholderConfigurer
			 */
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		// <2> 执行当前 Spring 应用上下文中的 BeanFactoryPostProcessor 处理器的 postProcessBeanFactory(beanFactory) 方法
		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// <3> 获取底层 BeanFactory 容器中所有 BeanFactoryPostProcessor 类型的 Bean 们
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			// 上面已经执行过了则跳过
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// <3.1> PriorityOrdered 类型的 BeanFactoryPostProcessor 对象
		// 排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 执行
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// <3.2> Ordered 类型的 BeanFactoryPostProcessor 对象
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 执行
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// <3.2> nonOrdered 的 BeanFactoryPostProcessor 对象
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 无需排序，直接执行
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	/**
	 * 1.获取所有 BeanPostProcessor 类型的 beanName
	 * 2.添加 BeanPostProcessor - BeanPostProcessorChecker,用于打印日志（所有 BeanPostProcessor 还没有全部实例化就有 Bean 初始化完成）
	 * 3.获取所有 BeanPostProcessor 实现类（依赖查找）,添加至 BeanFactory 容器中（顺序：PriorityOrdered > Ordered > 无）
	 * 4.注意,第 3 步添加的 BeanPostProcessor 如果是 MergedBeanDefinitionPostProcessor 类型,会再次添加（先移除再添加,也就是将顺序往后挪）
	 * 5.重新添加 BeanPostProcessor - ApplicationListenerDetector,目的将其移至最后,
	 * 因为这个后置处理器用于探测 ApplicationListener 类型的 Bean,需要保证 Bean 完全初始化,放置最后比较合适
	 *
	 * 对与上述第 4 步是否疑惑？我的理解是 MergedBeanDefinitionPostProcessor 主要是依赖注入的实现,
	 * 需要保证当前 Spring Bean 的相关初始化工作已完成,然后再进行依赖注入
	 *
	 * 总结:将所有已加载出来的 BeanPostProcessor 类型的 BeanDefinition 通过依赖查找获取到 Bean 们,然后有序的添加至 BeanFactory 中
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// <1> 获取所有的 BeanPostProcessor 类型的 beanName
		// 这些 beanName 都已经全部加载到容器中去,但是没有实例化
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// <2> 记录所有的 BeanPostProcessor 数量,为什么加 1 ？因为下面又添加了一个
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 注册 BeanPostProcessorChecker,它主要是用于在 BeanPostProcessor 实例化期间记录日志
		// 当 Spring 中配置的后置处理器还没有注册就已经开始了 bean 的实例化过程,这个时候便会打印 BeanPostProcessorChecker 中的内容
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// <3> 开始注册 BeanPostProcessor
		// 实现了 `PriorityOrdered` 接口的 BeanPostProcessor 对应的 Bean 集合
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// MergedBeanDefinitionPostProcessor 类型对应的 Bean 集合
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		// 实现了 `Ordered` 接口的 BeanPostProcessor 对应的 beanName 集合
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 没有顺序的 BeanPostProcessor 对应的 beanName 集合
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			// PriorityOrdered
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 调用 getBean(...) 方法获取该 BeanPostProcessor 处理器的 Bean 对象
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			// Ordered
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 无序
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 第一步,对所有实现了 PriorityOrdered 的 BeanPostProcessor 进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 进行注册,也就是添加至 DefaultListableBeanFactory 中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 第二步,获取所有实现了 Ordered 接口的 BeanPostProcessor 对应的 Bean 们
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			// 调用 getBean(...) 方法获取该 BeanPostProcessor 处理器的 Bean 对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 对所有实现了 Ordered 的 BeanPostProcessor 进行排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 进行注册，也就是添加至 DefaultListableBeanFactory 中
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 第三步注册所有无序的 BeanPostProcessor
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			// 调用 getBean(...) 方法获取该 BeanPostProcessor 处理器的 Bean 对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 注册,无需排序
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// 最后,注册所有的 MergedBeanDefinitionPostProcessor 类型的 Bean 们
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 重新注册 ApplicationListenerDetector（探测器）,用于探测内部 ApplicationListener 类型的 Bean
		// 在完全初始化 Bean 后,如果是 ApplicationListener 类型且为单例模式,则添加到 Spring 应用上下文
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
