/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Argumentを表現するクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Argument {
    /**
     * ServiceDescriptionのパース時に使用するビルダー
     *
     * @see Device#loadDescription()
     * @see Service#loadDescription(HttpClient)
     * @see Action.Builder
     */
    public static class Builder {
        private Action mAction;
        private String mName;
        private boolean mInputDirection;
        private String mRelatedStateVariableName;
        private StateVariable mRelatedStateVariable;

        /**
         * インスタンス作成
         */
        public Builder() {
        }

        /**
         * このArgumentを保持するActionを登録する。
         *
         * @param action このArgumentを保持するAction
         * @return Builder
         */
        public Builder setAction(@Nonnull Action action) {
            mAction = action;
            return this;
        }

        /**
         * Argument名を登録する。
         *
         * @param name Argument名
         * @return Builder
         */
        public Builder setName(@Nonnull String name) {
            mName = name;
            return this;
        }

        /**
         * Directionの値を登録する
         *
         * <p>"in"の場合のみinput、それ以外をoutputと判定する。
         *
         * @param direction Directionの値
         * @return Builder
         */
        public Builder setDirection(@Nonnull String direction) {
            mInputDirection = "in".equalsIgnoreCase(direction);
            return this;
        }

        /**
         * RelatedStateVariableの値を登録する。
         *
         * @param name RelatedStateVariableの値
         * @return Builder
         */
        public Builder setRelatedStateVariableName(@Nonnull String name) {
            mRelatedStateVariableName = name;
            return this;
        }

        /**
         * RelatedStateVariableの値を返す。
         *
         * @return RelatedStateVariableの値
         */
        @Nullable
        public String getRelatedStateVariableName() {
            return mRelatedStateVariableName;
        }

        /**
         * RelatedStateVariableので指定されたStateVariableのインスタンスを登録する。
         *
         * @param variable StateVariableのインスタンス
         * @return Builder
         */
        public Builder setRelatedStateVariable(@Nonnull StateVariable variable) {
            mRelatedStateVariable = variable;
            return this;
        }

        /**
         * Argumentのインスタンスを作成する。
         *
         * @return Argumentのインスタンス
         * @throws IllegalStateException 必須パラメータが設定されていない場合
         */
        @Nullable
        public Argument build() throws IllegalStateException {
            if (mAction == null) {
                throw new IllegalStateException("action must be set.");
            }
            if (mName == null) {
                throw new IllegalStateException("name must be set.");
            }
            if (mRelatedStateVariable == null) {
                throw new IllegalStateException("related state variable must be set.");
            }
            return new Argument(this);
        }
    }

    private final Action mAction;
    private final String mName;
    private final boolean mInputDirection;
    private final StateVariable mRelatedStateVariable;

    private Argument(@Nonnull Builder builder) {
        mAction = builder.mAction;
        mName = builder.mName;
        mInputDirection = builder.mInputDirection;
        mRelatedStateVariable = builder.mRelatedStateVariable;
    }

    /**
     * このArgumentを保持するActionを返す。
     *
     * @return このArgumentを保持するAction
     */
    @Nonnull
    public Action getAction() {
        return mAction;
    }

    /**
     * Argument名を返す。
     *
     * @return Argument名
     */
    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * Input方向か否かを返す。
     *
     * @return Inputの場合true
     */
    public boolean isInputDirection() {
        return mInputDirection;
    }

    /**
     * RelatedStateVariableで指定されたStateVariableのインスタンスを返す。
     *
     * @return StateVariableのインスタンス
     */
    @Nonnull
    public StateVariable getRelatedStateVariable() {
        return mRelatedStateVariable;
    }
}
