// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.expressions;

import org.apache.doris.nereids.exceptions.AnalysisException;
import org.apache.doris.nereids.exceptions.UnboundException;
import org.apache.doris.nereids.trees.AbstractTreeNode;
import org.apache.doris.nereids.trees.expressions.literal.Literal;
import org.apache.doris.nereids.trees.expressions.shape.LeafExpression;
import org.apache.doris.nereids.trees.expressions.typecoercion.ExpectsInputTypes;
import org.apache.doris.nereids.trees.expressions.typecoercion.TypeCheckResult;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.coercion.AbstractDataType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Abstract class for all Expression in Nereids.
 */
public abstract class Expression extends AbstractTreeNode<Expression> {

    private static final String INPUT_CHECK_ERROR_MESSAGE = "argument %d requires %s type, however '%s' is of %s type";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public Expression(Expression... children) {
        super(children);
    }

    public DataType getDataType() throws UnboundException {
        throw new UnboundException("dataType");
    }

    public String toSql() throws UnboundException {
        throw new UnboundException("sql");
    }

    public boolean nullable() throws UnboundException {
        throw new UnboundException("nullable");
    }

    public TypeCheckResult checkInputDataTypes() {
        if (this instanceof ExpectsInputTypes) {
            ExpectsInputTypes expectsInputTypes = (ExpectsInputTypes) this;
            return checkInputDataTypes(children, expectsInputTypes.expectedInputTypes());
        }
        return TypeCheckResult.SUCCESS;
    }

    private TypeCheckResult checkInputDataTypes(List<Expression> inputs, List<AbstractDataType> inputTypes) {
        Preconditions.checkArgument(inputs.size() == inputTypes.size());
        List<String> errorMessages = Lists.newArrayList();
        for (int i = 0; i < inputs.size(); i++) {
            Expression input = inputs.get(i);
            AbstractDataType inputType = inputTypes.get(i);
            if (!inputType.acceptsType(input.getDataType())) {
                errorMessages.add(String.format(INPUT_CHECK_ERROR_MESSAGE,
                        i + 1, inputType.simpleString(), input.toSql(), input.getDataType().simpleString()));
            }
        }
        if (!errorMessages.isEmpty()) {
            return new TypeCheckResult(false, StringUtils.join(errorMessages, ", "));
        }
        return TypeCheckResult.SUCCESS;
    }

    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    @Override
    public List<Expression> children() {
        return children;
    }

    @Override
    public Expression child(int index) {
        return children.get(index);
    }

    @Override
    public Expression withChildren(List<Expression> children) {
        throw new RuntimeException();
    }

    public final Expression withChildren(Expression... children) {
        return withChildren(ImmutableList.copyOf(children));
    }

    /**
     * Whether the expression is a constant.
     */
    public final boolean isConstant() {
        if (this instanceof LeafExpression) {
            return this instanceof Literal;
        } else {
            return children().stream().allMatch(Expression::isConstant);
        }
    }

    public final Expression castTo(DataType targetType) throws AnalysisException {
        return uncheckedCastTo(targetType);
    }

    protected Expression uncheckedCastTo(DataType targetType) throws AnalysisException {
        throw new RuntimeException("Do not implement uncheckedCastTo");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Expression that = (Expression) o;
        return Objects.equals(children(), that.children());
    }

    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * Return true if all the SlotRef in the expr tree is bound to the same column.
     */
    public boolean boundToColumn(String column) {
        for (Expression child : children) {
            if (!child.boundToColumn(column)) {
                return false;
            }
        }
        return true;
    }

    public Expression leftMostNode() {
        Expression leftChild = this;
        while (leftChild.children.size() > 0) {
            leftChild = leftChild.child(0);
        }
        return leftChild;
    }
}
