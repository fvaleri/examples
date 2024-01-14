package it.fvaleri.example;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class Main {
    public static void main(String[] args) {
        try {
            // get the agent
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();

            // register the MBean instance
            ObjectName name = new ObjectName("it.fvaleri.example:type=standard,name=dog");
            server.registerMBean(new Dog(), name);

            // invoke operation with no parameters and signature
            Object result = server.invoke(name, "toString", null, null);
            System.out.println(result);

            // create and set the list of management attributes
            AttributeList list = new AttributeList();
            list.add(new Attribute("Name", "Balto"));
            list.add(new Attribute("Age", 6));
            server.setAttributes(name, list);

            // Invoke the printInfo to retrieve the updated state
            result = server.invoke(name, "toString", null, null);
            System.out.println(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // the MBean suffix is required
    public interface DogMBean {
        String getName();
        void setName(String newName);
        int getAge();
        void setAge(int newAge);
        String toString();
    }

    public static class Dog implements DogMBean {
        private String name;
        private int age;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String newName) {
            this.name = newName;
        }

        @Override
        public int getAge() {
            return age;
        }

        @Override
        public void setAge(int newAge) {
            this.age = newAge;
        }

        @Override
        public String toString() {
            return "Dog{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
        }
    }
}
