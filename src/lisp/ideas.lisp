(def LinkedList "io/github/whetfire/lateral/LinkedList")

LinkedList

(defun str-concat (:rest lst)
  (asm (:new "java/lang/StringBuilder")
       :dup
       (:invokespecial "java/lang/StringBuilder" "<init>" "()V")
       (de-asm lst)
       (:goto testlab)
       (:label looplab)
       ; StringBuilder, List
       :dup2
       ; StringBuilder, List, StringBuilder, List
       (:invokespecial LinkedList "getValue" "()Ljava/lang/Object;")
       (:invokespecial Object "toString" "()Ljava/lang/String;")
       ; StringBuilder, List, StringBuilder, String
       (:invokespecial StringBuilder "append" "()Ljava/lang/StringBuilder")
       :pop
       ; StringBuilder, List
       (:label testlab)
       ; if lst != null && lst.next != null
       :dup
       ; StringBuilder, List, List
       (:ifnull endlab)
       :dup
       (:invokespecial LinkedList "getNext" "()Lio/github/whetfire/lateral/LinkedList;")
       ; StringBuilder, List, List
       (:ifnonnull looplab)
       (:label endlab)
       ; StringBuilder, List
       :pop
       (:invokespecial Object "toString" "()Ljava/lang/String;")
       :areturn))

(defmacro prep (a b)
  (let (LinkedList     "io/github/whetfire/lateral/LinkedList"
        LinkedListType "Lio/github/whetfire/lateral/LinkedList;"
        ObjectType     "Ljava/lang/Object;")
    (list (quote asm)
          (list :new LinkedList)
          :dup
          (list (quote de-asm) a)
          (list (quote de-asm) b)
          (list :checkcast LinkedList)
          (list :invokespecial LinkedList "<init>"
                "(Ljava/lang/Object;Lio/github/whetfire/lateral/LinkedList;)V"))))

;; alternative asm synatax
; make it work like quote-unquote
; perhaps rename asm to asm-quote ?
(defmacro prep (a b)
  (asm ,b
       (:checkcast Sequence)
       ,a
       (:invokevirtual Sequence.cons(Object))))

(defun cons (a b)
  (prep a b))

cons

(cons 1 null)

(defun range (lo hi)
  (asm (de-asm '())
       (de-asm hi)
       (:checkcast "java/lang/Integer")
       (:goto test)
       (:label loop)
       (de-asm (stack-call dec 1))
       (:checkcast "java/lang/Integer")
       :dup_x1
       (de-asm (stack-call cons 1))
       :swqp
       (:label test)
       :dup
       (de-asm low)
       (:checkcast "java/lang/Integer")
       (:invokevirtual "java/lang/Integer" "compareTo" "(Ljava/lang/Integer;)I")
       (:ifgt loop)
       :pop))
