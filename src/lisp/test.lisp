(defun gensym ()
  (asm (:invokestatic "io/github/whetfire/lateral/Symbol"
                      "gensym"
                      "()Lio/github/whetfire/lateral/Symbol;")))

(defun concat (:rest lst)
  (asm (de-asm lst)
       (:checkcast "io/github/whetfire/lateral/Sequence")
       (:invokestatic "io/github/whetfire/lateral/Sequence"
                      "concat"
                      "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(defmacro while (condition body)
  (let (looplab  (gensym)
        startlab (gensym))
    `(asm (:goto      ,looplab)
          (:label     ,startlab)
          (de-asm     ,body)
          (:label     ,looplab)
          (de-asm     ,condition)
          (:ifnonnull ,startlab)
          :aconst_null)))

(defmacro asm-if (condition then else)
  (let (elselab (gensym)
        endlab  (gensym))
    `(asm (de-asm  ,condition)
          (:ifnull ,elselab)
          (de-asm  ,then)
          (:goto   ,endlab)
          (:label  ,elselab)
          (de-asm  ,else)
          (:label  ,endlab))))

(defun str-concat (:rest st)
  (asm (:new "java/lang/StringBuilder")
       :dup
       (:invokespecial "java/lang/StringBuilder" "<init>" "()V")
       (de-asm st)
       (:goto cond)
       (:label loop)
       :dup2
       (:invokevirtual "io/github/whetfire/lateral/Sequence" "first"
                       "()Ljava/lang/Object;")
       (:invokevirtual "Object" "toString" "()Ljava/lang/String;")
       (:invokevirtual "StringBuilder" "append"
                       "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
       :pop
       (:label cond)
       :dup
       (:invokevirtual "Sequence" "isEmpty" "()Z")
       (:ifeq loop)
       :pop
       (:invokevirtual "Object" "toString" "()Ljava/lang/String;")))

(defmacro prep (a b)
  `(asm (de-asm ,b)
        (:checkcast "io/github/whetfire/lateral/Sequence")
        (de-asm ,a)
        (:invokevirtual "io/github/whetfire/lateral/Sequence"
                        "cons"
                        "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(list (gensym) (gensym))
(asm-if null
  (list 1 2)
  '())
