package generator;

import cn.t.util.common.StringUtil;
import constants.ClassConstants;
import constants.FieldConstants;
import constants.PackageConstants;
import generator.api.dom.java.ExtendedFullyQualifiedJavaType;
import generator.codegen.ExtendedRootClassInfo;
import org.mybatis.generator.api.CommentGenerator;
import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.Plugin;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.mybatis3.model.BaseRecordGenerator;
import org.mybatis.generator.internal.util.JavaBeansUtil;
import util.IntrospectedTableUtil;
import util.JavaModelGeneratorUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mybatis.generator.internal.util.JavaBeansUtil.*;
import static org.mybatis.generator.internal.util.messages.Messages.getString;

/**
 * 实体类生成器扩展，被ExtendedIntrospectedTableMyBatis3Impl加载
 * */
public class ExtendedBaseRecordGenerator extends BaseRecordGenerator {

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.8", table.toString()));
        Plugin plugins = context.getPlugins();
        CommentGenerator commentGenerator = context.getCommentGenerator();
        String baseRecordType = introspectedTable.getBaseRecordType();
        FullyQualifiedJavaType type;
        if(JavaModelGeneratorUtil.generateBaseRecordClass(context.getJavaModelGeneratorConfiguration())) {
            int shortBaseRecordTypeIndex = baseRecordType.lastIndexOf(".") + 1;
            String shortBaseRecordType = baseRecordType.substring(shortBaseRecordTypeIndex);
            String typeStr = baseRecordType.substring(0, shortBaseRecordTypeIndex).concat(PackageConstants.BASE_RECORD_CLASS_PACKAGE).concat(".").concat(shortBaseRecordType).concat(ClassConstants.BASE_CLASS_SUFFIX);
            type = new FullyQualifiedJavaType(typeStr);
        } else {
            type = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        }
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        commentGenerator.addJavaFileComment(topLevelClass);

        FullyQualifiedJavaType superClass = getSuperClass();
        if (superClass != null) {
            String primaryKeyType = JavaModelGeneratorUtil.getPrimaryKeyType(introspectedTable);
            if(!StringUtil.isEmpty(primaryKeyType)) {
                superClass.addTypeArgument(new FullyQualifiedJavaType(primaryKeyType));
            }
            topLevelClass.setSuperClass(superClass);
            topLevelClass.addImportedType(superClass);
        }
        setUnionKeyAsField(topLevelClass);
        commentGenerator.addModelClassComment(topLevelClass, introspectedTable);

        List<IntrospectedColumn> introspectedColumns = getColumnsInThisClass();

        if (introspectedTable.isConstructorBased()) {
            addParameterizedConstructor(topLevelClass, introspectedTable.getNonBLOBColumns());
            if (includeBLOBColumns()) {
                addParameterizedConstructor(topLevelClass, introspectedTable.getAllColumns());
            }
            if (!introspectedTable.isImmutable()) {
                addDefaultConstructor(topLevelClass);
            }
        }

        String rootClass = getRootClass();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            if (ExtendedRootClassInfo.getInstance(rootClass, warnings)
                .containsProperty(introspectedColumn)) {
                continue;
            }
            Field field = getJavaBeansField(introspectedColumn, context, introspectedTable);
            if (plugins.modelFieldGenerated(field, topLevelClass, introspectedColumn, introspectedTable, Plugin.ModelClassType.BASE_RECORD)) {
                topLevelClass.addField(field);
                topLevelClass.addImportedType(field.getType());
            }
            Method method = getJavaBeansGetter(introspectedColumn, context, introspectedTable);
            if (plugins.modelGetterMethodGenerated(method, topLevelClass, introspectedColumn, introspectedTable, Plugin.ModelClassType.BASE_RECORD)) {
                topLevelClass.addMethod(method);
            }
            if (!introspectedTable.isImmutable()) {
                method = getJavaBeansSetter(introspectedColumn, context, introspectedTable);
                if (plugins.modelSetterMethodGenerated(method, topLevelClass, introspectedColumn, introspectedTable, Plugin.ModelClassType.BASE_RECORD)) {
                    topLevelClass.addMethod(method);
                }
            }
        }

        List<CompilationUnit> answer = new ArrayList<>();
        if (context.getPlugins().modelBaseRecordClassGenerated(topLevelClass, introspectedTable)) {
            answer.add(topLevelClass);
            //补充真正要使用的类
            if(JavaModelGeneratorUtil.generateBaseRecordClass(context.getJavaModelGeneratorConfiguration())) {
                TopLevelClass subTopLevelClass = new TopLevelClass(baseRecordType);
                subTopLevelClass.setVisibility(JavaVisibility.PUBLIC);
                subTopLevelClass.setSuperClass(type);
                subTopLevelClass.setSuperClass(type);
                subTopLevelClass.addImportedType(type);
                context.getPlugins().modelBaseRecordClassGenerated(subTopLevelClass, introspectedTable);
                answer.add(subTopLevelClass);
            }
        }
        return answer;
    }

    /**
     * 此处修改了父类的实现从而使实体类不再继承主键类,并重写了getFullyQualifiedName方法
     * */
    private FullyQualifiedJavaType getSuperClass() {
        String rootClass = getRootClass();
        if (rootClass != null) {
            return new ExtendedFullyQualifiedJavaType(rootClass);
        }
        return null;
    }

    private List<IntrospectedColumn> getColumnsInThisClass() {
        List<IntrospectedColumn> introspectedColumns;
        if (includePrimaryKeyColumns()) {
            if (includeBLOBColumns()) {
                introspectedColumns = introspectedTable.getAllColumns();
            } else {
                introspectedColumns = introspectedTable.getNonBLOBColumns();
            }
        } else {
            if (includeBLOBColumns()) {
                introspectedColumns = introspectedTable
                    .getNonPrimaryKeyColumns();
            } else {
                introspectedColumns = introspectedTable.getBaseColumns();
            }
        }

        return introspectedColumns;
    }

    private void addParameterizedConstructor(TopLevelClass topLevelClass, List<IntrospectedColumn> constructorColumns) {
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setConstructor(true);
        method.setName(topLevelClass.getType().getShortName());
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);

        for (IntrospectedColumn introspectedColumn : constructorColumns) {
            method.addParameter(new Parameter(introspectedColumn.getFullyQualifiedJavaType(),
                introspectedColumn.getJavaProperty()));
            topLevelClass.addImportedType(introspectedColumn.getFullyQualifiedJavaType());
        }

        StringBuilder sb = new StringBuilder();
        List<String> superColumns = new LinkedList<>();
        if (introspectedTable.getRules().generatePrimaryKeyClass()) {
            boolean comma = false;
            sb.append("super(");
            for (IntrospectedColumn introspectedColumn : introspectedTable.getPrimaryKeyColumns()) {
                if (comma) {
                    sb.append(", ");
                } else {
                    comma = true;
                }
                sb.append(introspectedColumn.getJavaProperty());
                superColumns.add(introspectedColumn.getActualColumnName());
            }
            sb.append(");");
            method.addBodyLine(sb.toString());
        }

        for (IntrospectedColumn introspectedColumn : constructorColumns) {
            if (!superColumns.contains(introspectedColumn.getActualColumnName())) {
                sb.setLength(0);
                sb.append("this.");
                sb.append(introspectedColumn.getJavaProperty());
                sb.append(" = ");
                sb.append(introspectedColumn.getJavaProperty());
                sb.append(';');
                method.addBodyLine(sb.toString());
            }
        }

        topLevelClass.addMethod(method);
    }

    private boolean includePrimaryKeyColumns() {
        return !introspectedTable.getRules().generatePrimaryKeyClass()
            && introspectedTable.hasPrimaryKeyColumns();
    }

    private boolean includeBLOBColumns() {
        return !introspectedTable.getRules().generateRecordWithBLOBsClass()
            && introspectedTable.hasBLOBColumns();
    }

    /**
     * 如果是联合主键则取消实体类继承关系，将主键类添加到实体类中的字段中
     * */
    private void setUnionKeyAsField(TopLevelClass topLevelClass) {
        if (IntrospectedTableUtil.isUnionKeyTable(introspectedTable)) {
            String primaryKeyType = introspectedTable.getPrimaryKeyType();
            if (primaryKeyType != null) {
                FullyQualifiedJavaType unionKeyClass = new FullyQualifiedJavaType(primaryKeyType);

                //添加主键为属性属性
                Field unionKeyField = new Field(FieldConstants.UNION_KEY_PROPERTY_NAME, unionKeyClass);
                unionKeyField.setVisibility(JavaVisibility.PRIVATE);
                topLevelClass.addField(unionKeyField);
                topLevelClass.addImportedType(unionKeyClass);

                //getter
                Method getter = new Method();
                getter.setVisibility(JavaVisibility.PUBLIC);
                getter.setReturnType(unionKeyClass);
                getter.setName(JavaBeansUtil.getGetterMethodName(unionKeyField.getName(), unionKeyClass));

                StringBuilder getterBuilder = new StringBuilder();
                getterBuilder.append("return this.");
                getterBuilder.append(unionKeyField.getName());
                getterBuilder.append(';');
                getter.addBodyLine(getterBuilder.toString());
                topLevelClass.addMethod(getter);

                //setter
                Method setter = new Method();
                setter.setVisibility(JavaVisibility.PUBLIC);
                setter.addParameter(new Parameter(unionKeyClass, unionKeyField.getName()));
                setter.setName(JavaBeansUtil.getSetterMethodName(unionKeyField.getName()));
                StringBuilder setterBuilder = new StringBuilder();
                setterBuilder.append("this.");
                setterBuilder.append(unionKeyField.getName());
                setterBuilder.append(" = ");
                setterBuilder.append(unionKeyField.getName());
                setterBuilder.append(';');
                setter.addBodyLine(setterBuilder.toString());
                topLevelClass.addMethod(setter);
            }
        }
    }

}
